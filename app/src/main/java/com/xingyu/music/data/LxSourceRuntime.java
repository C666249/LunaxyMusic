package com.xingyu.music.data;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.xingyu.music.model.Song;
import com.xingyu.music.model.SourceVariant;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * LX Mobile 2.0 compatibility host for custom-source scripts.
 *
 * V2 keeps WebView as the JS engine but mirrors the public preload contract much more
 * closely: source init filtering, request callback shape, Uint8Array buffer/crypto,
 * binary HTTP, AES/RSA modes and the dynamic-code restrictions used by LX Mobile.
 * It deliberately does not claim byte-for-byte QuickJS engine parity.
 */
public final class LxSourceRuntime {
    public static final String LX_CUSTOM_API_VERSION = "2.0.0";

    public static final class RuntimeResult {
        public final String url, quality, source;
        RuntimeResult(String url, String quality, String source) {
            this.url = url; this.quality = quality; this.source = source;
        }
    }

    private final Context context;
    private final ResolverProvider provider;
    private final SourceScriptStore scriptStore;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newFixedThreadPool(3);
    private final CountDownLatch ready = new CountDownLatch(1);
    private final ConcurrentHashMap<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
    private volatile WebView webView;
    private volatile String initError;
    private volatile JSONObject sourceInfo;
    private volatile boolean destroyed;

    public LxSourceRuntime(Context context, ResolverProvider provider) {
        if (context == null) throw new IllegalArgumentException("context == null");
        this.context = context.getApplicationContext();
        this.provider = provider;
        this.scriptStore = new SourceScriptStore(this.context);
        worker.submit(() -> {
            try {
                String source = scriptStore.load(provider);
                main.post(() -> createRuntime(source));
            } catch (Exception e) {
                initError = safe(e);
                ready.countDown();
            }
        });
    }

    public boolean isReady() { return ready.getCount() == 0 && initError == null; }
    public String initError() { return initError; }
    public JSONObject sourceInfo() { return sourceInfo; }

    /** Non-blocking snapshot only; used by diagnostics and never initializes a source. */
    public String capabilitySummary() {
        if (destroyed) return "Runtime 已关闭";
        if (ready.getCount() > 0) return "脚本初始化中";
        if (initError != null) return "初始化失败";
        JSONObject info = sourceInfo;
        JSONObject sources = info == null ? null : info.optJSONObject("sources");
        if (sources == null) return "脚本未声明能力";
        StringBuilder b = new StringBuilder();
        for (String platform : new String[]{"tx","wy","kw","kg","mg"}) {
            JSONObject item = sources.optJSONObject(platform);
            if (item == null) continue;
            if (b.length() > 0) b.append(" · ");
            b.append(platform);
            JSONArray qs = item.optJSONArray("qualitys");
            if (qs != null && qs.length() > 0) {
                b.append('[');
                for (int i = 0; i < qs.length(); i++) {
                    if (i > 0) b.append('/');
                    b.append(qs.optString(i, ""));
                }
                b.append(']');
            }
        }
        return b.length() == 0 ? "未声明可用音乐平台" : b.toString();
    }

    public boolean supportsPlatform(String platform) throws Exception {
        if (destroyed) return false;
        if (!ready.await(14, TimeUnit.SECONDS)) throw new Exception(provider.label + " 音源初始化超时");
        if (initError != null) throw new Exception(provider.label + " 初始化失败：" + initError);
        JSONObject info = sourceInfo;
        if (info == null) return true;
        JSONObject sources = info.optJSONObject("sources");
        if (sources == null) return true;
        return sources.optJSONObject(platform) != null;
    }

    public boolean supportsRoute(String platform, String quality) throws Exception {
        if (destroyed) return false;
        if (!ready.await(14, TimeUnit.SECONDS)) throw new Exception(provider.label + " 音源初始化超时");
        if (initError != null) throw new Exception(provider.label + " 初始化失败：" + initError);
        return supports(platform, quality);
    }

    public RuntimeResult musicUrl(SourceVariant variant, Song song, String quality) throws Exception {
        if (destroyed) throw new Exception(provider.label + " 音源环境已关闭");
        if (!ready.await(14, TimeUnit.SECONDS)) throw new Exception(provider.label + " 音源初始化超时");
        if (initError != null) throw new Exception(provider.label + " 初始化失败：" + initError);
        if (variant == null || variant.sourceId.isEmpty()) throw new Exception("歌曲缺少平台 ID");
        if (!supports(variant.source, quality)) throw new Exception("UNSUPPORTED " + variant.source + "/" + quality);

        // Critical V8 change: pass the provider-native object instead of rebuilding a tiny subset.
        JSONObject musicInfo = variant.musicInfo(song);
        JSONObject info = new JSONObject();
        info.put("type", quality);
        info.put("musicInfo", musicInfo);

        String requestId = UUID.randomUUID().toString();
        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(requestId, future);
        String js = "globalThis.__lxInvoke(" + JSONObject.quote(requestId) + ","
                + JSONObject.quote(variant.source) + "," + JSONObject.quote("musicUrl") + ","
                + JSONObject.quote(info.toString()) + ");";
        main.post(() -> {
            WebView w = webView;
            if (w == null || destroyed) {
                CompletableFuture<String> f = pending.remove(requestId);
                if (f != null) f.completeExceptionally(new Exception(provider.label + " JS Runtime 不可用"));
                return;
            }
            try { w.evaluateJavascript(js, null); }
            catch (Exception e) {
                CompletableFuture<String> f = pending.remove(requestId);
                if (f != null) f.completeExceptionally(e);
            }
        });

        try {
            String url = future.get(34, TimeUnit.SECONDS);
            if (url == null || !url.startsWith("http")) throw new Exception(provider.label + " 返回了无效播放地址");
            return new RuntimeResult(url, quality, variant.source);
        } catch (java.util.concurrent.TimeoutException e) {
            pending.remove(requestId);
            throw new Exception(provider.label + " 获取播放地址超时");
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            throw new Exception(cause == null || cause.getMessage() == null ? provider.label + " 解析失败" : cause.getMessage());
        }
    }

    private boolean supports(String platform, String quality) {
        JSONObject info = sourceInfo;
        if (info == null) return true;
        JSONObject sources = info.optJSONObject("sources");
        if (sources == null) return true;
        JSONObject item = sources.optJSONObject(platform);
        if (item == null) return false;
        JSONArray qs = item.optJSONArray("qualitys");
        if (qs == null) qs = item.optJSONArray("qualities");
        if (qs == null || qs.length() == 0) return true;
        for (int i = 0; i < qs.length(); i++) {
            if (quality.equalsIgnoreCase(qs.optString(i, ""))) return true;
        }
        return false;
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void createRuntime(String source) {
        if (destroyed) return;
        try {
            WebView w = new WebView(context);
            WebSettings settings = w.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(false);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setBlockNetworkLoads(true);
            w.addJavascriptInterface(new NativeHttpBridge(), "NativeHttpBridge");
            w.addJavascriptInterface(new NativeSourceBridge(), "NativeSourceBridge");
            w.addJavascriptInterface(new NativeCryptoBridge(), "NativeCryptoBridge");
            w.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView view, String url) {
                    String bootstrap = bootstrapScript(source);
                    view.evaluateJavascript(bootstrap + "\ntry{\n" + source
                            + "\n}catch(e){NativeSourceBridge.onFatal(String(e&&e.stack||e&&e.message||e));}", null);
                }
            });
            webView = w;
            w.loadUrl("about:blank");
        } catch (Exception e) {
            initError = safe(e);
            ready.countDown();
        }
    }

    private String bootstrapScript(String rawSource) {
        String rawQuoted = JSONObject.quote(rawSource == null ? "" : rawSource);
        String parsedName = scriptMeta(rawSource, "name");
        String nameQuoted = JSONObject.quote(parsedName.isEmpty() ? provider.label : parsedName);
        String versionQuoted = JSONObject.quote(scriptMeta(rawSource, "version"));
        String authorQuoted = JSONObject.quote(scriptMeta(rawSource, "author"));
        String descQuoted = JSONObject.quote(scriptMeta(rawSource, "description"));
        String homepageQuoted = JSONObject.quote(scriptMeta(rawSource, "homepage"));
        String template = """
                (function(){
                  'use strict';
                  const __handlers = Object.create(null);
                  const __httpCallbacks = Object.create(null);
                  let __httpSeq = 0;
                  let __didInit = false;
                  let __didUpdateAlert = false;
                  const __EVENT_NAMES = Object.freeze({ request:'request', inited:'inited', updateAlert:'updateAlert' });
                  const __KNOWN_SOURCES = ['kw','kg','tx','wy','mg','local'];
                  function __errText(e){ return String(e && (e.stack || e.message || e) || 'unknown error'); }
                  function __asBytes(v, enc){
                    if (v instanceof Uint8Array) return new Uint8Array(v);
                    if (Array.isArray(v)) return new Uint8Array(v.map(function(x){return Number(x)&255;}));
                    if (v && typeof v.length === 'number' && typeof v !== 'string') return new Uint8Array(Array.from(v));
                    return new Uint8Array(JSON.parse(NativeCryptoBridge.bufferFrom(String(v == null ? '' : v), String(enc || 'utf8'))));
                  }
                  const __buffer = Object.freeze({
                    from:function(v, enc){ return __asBytes(v, enc); },
                    bufToString:function(v, format){
                      format=String(format || 'utf8').toLowerCase();
                      if (format === 'binary') return v;
                      if (typeof v === 'string' && (format === 'utf8' || format === 'utf-8')) return v;
                      return NativeCryptoBridge.bufferToString(JSON.stringify(Array.from(__asBytes(v, 'utf8'))), format);
                    }
                  });
                  function __byteJson(v){ return JSON.stringify(Array.from(__asBytes(v, 'utf8'))); }
                  function __normaliseSourceInfo(data){
                    if (!data || typeof data !== 'object' || !data.sources || typeof data.sources !== 'object')
                      throw new Error('Missing required parameter init info');
                    const allowedQualitys={
                      kw:['128k','320k','flac','flac24bit'], kg:['128k','320k','flac','flac24bit'],
                      tx:['128k','320k','flac','flac24bit'], wy:['128k','320k','flac','flac24bit'],
                      mg:['128k','320k','flac','flac24bit'], local:[]
                    };
                    const allowedActions={kw:['musicUrl'],kg:['musicUrl'],tx:['musicUrl'],wy:['musicUrl'],mg:['musicUrl'],local:['musicUrl','lyric','pic']};
                    const out={sources:{}};
                    __KNOWN_SOURCES.forEach(function(key){
                      const item=data.sources[key];
                      if(!item || item.type !== 'music') return;
                      if(!Array.isArray(item.actions) || !Array.isArray(item.qualitys)) throw new Error('invalid source info: '+key);
                      out.sources[key]={
                        type:'music',
                        actions:allowedActions[key].filter(function(a){return item.actions.indexOf(a)>=0;}),
                        qualitys:allowedQualitys[key].filter(function(q){return item.qualitys.indexOf(q)>=0;})
                      };
                    });
                    return out;
                  }
                  const __crypto = Object.freeze({
                    md5:function(v){ return NativeCryptoBridge.md5(String(v == null ? '' : v)); },
                    randomBytes:function(size){ return new Uint8Array(JSON.parse(NativeCryptoBridge.randomBytes(Number(size)||16))); },
                    aesEncrypt:function(buffer, mode, key, iv){
                      return new Uint8Array(JSON.parse(NativeCryptoBridge.aesEncrypt(__byteJson(buffer), String(mode||''), __byteJson(key), __byteJson(iv == null ? [] : iv))));
                    },
                    rsaEncrypt:function(buffer, key){
                      return new Uint8Array(JSON.parse(NativeCryptoBridge.rsaEncrypt(__byteJson(buffer), String(key == null ? '' : key))));
                    }
                  });
                  const __utils = Object.freeze({buffer:__buffer, crypto:__crypto});
                  const __scriptInfo = Object.freeze({
                    name:__SOURCE_NAME__, description:__SOURCE_DESC__, version:__SOURCE_VERSION__,
                    author:__SOURCE_AUTHOR__, homepage:__SOURCE_HOMEPAGE__, rawScript:__RAW_SOURCE__
                  });
                  const __lx = {
                    version:'2.0.0',
                    env:'mobile',
                    currentScriptInfo:__scriptInfo,
                    EVENT_NAMES:__EVENT_NAMES,
                    on:function(name, handler){
                      return new Promise(function(resolve, reject){
                        try {
                          name=String(name); if (name !== __EVENT_NAMES.request) throw new Error('invalid event: '+name);
                          if (typeof handler !== 'function') throw new Error('event handler must be function');
                          __handlers[name]=handler; resolve();
                        } catch(e){ reject(e); }
                      });
                    },
                    send:function(name, data){
                      return new Promise(function(resolve, reject){
                        try {
                          name=String(name);
                          if (name !== __EVENT_NAMES.inited && name !== __EVENT_NAMES.updateAlert) throw new Error('invalid event: '+name);
                          if (name === __EVENT_NAMES.inited) {
                            if (__didInit) throw new Error('inited event can only be sent once');
                            __didInit=true;
                            try { data=__normaliseSourceInfo(data); }
                            catch(initErr) { NativeSourceBridge.onFatal(__errText(initErr)); resolve(); return; }
                          } else {
                            if (__didUpdateAlert) throw new Error('updateAlert event can only be sent once');
                            __didUpdateAlert=true;
                          }
                          NativeSourceBridge.onSend(name, JSON.stringify(data == null ? null : data)); resolve();
                        } catch(e){ reject(e); }
                      });
                    },
                    request:function(url, options, callback){
                      if (typeof options === 'function') { callback=options; options={}; }
                      options=options || {};
                      if (typeof callback !== 'function') throw new Error('lx.request callback required');
                      const id='h'+(++__httpSeq)+'_'+Date.now();
                      __httpCallbacks[id]=callback;
                      NativeHttpBridge.request(id, String(url), JSON.stringify(options));
                      return function(){ try{NativeHttpBridge.cancel(id);}catch(e){} delete __httpCallbacks[id]; };
                    },
                    utils:__utils
                  };
                  Object.freeze(__lx);
                  globalThis.lx=__lx;
                  // LX mobile runs sources in QuickJS with dynamic code execution disabled.
                  // WebView remains our host engine, but expose the same restriction surface.
                  try { globalThis.eval=function(){ throw new Error('eval is not available'); }; } catch(e){}
                  try {
                    const __fnCtor = Function.prototype.constructor;
                    const __blockedCtor = new Proxy(__fnCtor, {
                      apply:function(){ throw new Error('Dynamic code execution is not allowed.'); },
                      construct:function(){ throw new Error('Dynamic code execution is not allowed.'); }
                    });
                    Object.defineProperty(Function.prototype,'constructor',{value:__blockedCtor,writable:false,configurable:false,enumerable:false});
                    globalThis.Function=__blockedCtor;
                  } catch(e){}
                  globalThis.__lxHttpComplete=function(id,error,response,body){
                    const cb=__httpCallbacks[id]; if(!cb)return; delete __httpCallbacks[id];
                    try {
                      let realBody = body == null ? (response && response.body) : body;
                      if (response && response.__binary && Array.isArray(realBody)) realBody = new Uint8Array(realBody);
                      if (response) { try { delete response.__binary; } catch(e){} response.body=realBody; }
                      cb(error ? new Error(String(error)) : null, response || null, realBody);
                    } catch(e){ console.error(e); }
                  };
                  globalThis.__lxInvoke=function(id,source,action,infoJson){
                    try {
                      const handler=__handlers[__EVENT_NAMES.request];
                      if(!handler) throw new Error('source request handler missing');
                      const info=JSON.parse(infoJson);
                      Promise.resolve(handler({source:String(source), action:String(action), info:info}))
                        .then(function(result){
                          if (action === 'musicUrl' && (typeof result !== 'string' || result.length > 2048 || !/^https?:\\/\\//i.test(result)))
                            throw new Error('invalid musicUrl result');
                          NativeSourceBridge.onResult(id,true,JSON.stringify(result));
                        })
                        .catch(function(err){NativeSourceBridge.onResult(id,false,JSON.stringify(__errText(err)));});
                    } catch(err){NativeSourceBridge.onResult(id,false,JSON.stringify(__errText(err)));}
                  };
                })();
                """;
        return template.replace("__RAW_SOURCE__", rawQuoted)
                .replace("__SOURCE_NAME__", nameQuoted)
                .replace("__SOURCE_DESC__", descQuoted)
                .replace("__SOURCE_VERSION__", versionQuoted)
                .replace("__SOURCE_AUTHOR__", authorQuoted)
                .replace("__SOURCE_HOMEPAGE__", homepageQuoted);
    }

    private final class NativeSourceBridge {
        @JavascriptInterface public void onSend(String event, String dataJson) {
            if ("inited".equals(event)) {
                try { sourceInfo = dataJson == null ? null : new JSONObject(dataJson); }
                catch (Exception ignored) { }
                ready.countDown();
            }
        }

        @JavascriptInterface public void onFatal(String error) {
            if (ready.getCount() > 0) {
                initError = error == null ? "unknown" : error;
                ready.countDown();
            }
        }

        @JavascriptInterface public void onResult(String id, boolean ok, String payloadJson) {
            CompletableFuture<String> f = pending.remove(id);
            if (f == null) return;
            try {
                Object value = new JSONTokener(payloadJson == null ? "null" : payloadJson).nextValue();
                String text = value == JSONObject.NULL ? "" : String.valueOf(value);
                if (ok) f.complete(text);
                else f.completeExceptionally(new Exception(normalizeSourceError(text)));
            } catch (Exception e) {
                if (ok) f.complete(payloadJson == null ? "" : payloadJson);
                else f.completeExceptionally(e);
            }
        }
    }

    private final class NativeHttpBridge {
        private final ConcurrentHashMap<String, Thread> running = new ConcurrentHashMap<>();

        @JavascriptInterface public void request(String id, String url, String optionsJson) {
            worker.submit(() -> {
                running.put(id, Thread.currentThread());
                try {
                    JSONObject options = optionsJson == null || optionsJson.isEmpty() ? new JSONObject() : new JSONObject(optionsJson);
                    String method = options.optString("method", "GET").toUpperCase(java.util.Locale.ROOT);
                    Map<String,String> headers = jsonHeaders(options.optJSONObject("headers"));
                    int timeout = Math.max(1500, Math.min(60000, options.optInt("timeout", 25000)));
                    Http.Response response;
                    Object bodyObj = options.opt("body");
                    String bodyText = bodyObj == null || bodyObj == JSONObject.NULL ? ""
                            : (bodyObj instanceof JSONObject || bodyObj instanceof JSONArray ? bodyObj.toString() : String.valueOf(bodyObj));

                    if ("GET".equals(method)) {
                        response = Http.get(url, headers, Math.min(timeout, 18000), timeout);
                    } else if ("POST".equals(method) && options.optJSONObject("formData") != null) {
                        response = Http.postMultipart(url, headers, jsonHeaders(options.optJSONObject("formData")), timeout);
                    } else if ("POST".equals(method) && options.optJSONObject("form") != null) {
                        response = Http.postForm(url, headers, jsonHeaders(options.optJSONObject("form")));
                    } else if ("POST".equals(method)) {
                        response = Http.postRaw(url, headers, bodyText, timeout);
                    } else {
                        throw new Exception("lx.request 暂不支持 method=" + method);
                    }

                    JSONObject resp = new JSONObject();
                    resp.put("statusCode", response.code);
                    resp.put("statusMessage", "");
                    resp.put("headers", new JSONObject(response.headers));
                    boolean binary = options.optBoolean("binary", false);
                    Object decoded = binary ? byteArrayJsonArray(response.bodyBytes) : decodeBody(response.body);
                    resp.put("body", decoded);
                    if (binary) resp.put("__binary", true);
                    completeHttp(id, null, resp, decoded);
                } catch (Exception e) {
                    completeHttp(id, safe(e), null, null);
                } finally {
                    running.remove(id);
                }
            });
        }

        @JavascriptInterface public void cancel(String id) {
            Thread t = running.remove(id);
            if (t != null) t.interrupt();
        }
    }

    private static final class NativeCryptoBridge {
        private final SecureRandom random = new SecureRandom();

        @JavascriptInterface public String md5(String s) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] d = md.digest((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
                StringBuilder b = new StringBuilder();
                for (byte x : d) b.append(String.format(java.util.Locale.ROOT, "%02x", x & 0xff));
                return b.toString();
            } catch (Exception e) { return ""; }
        }

        @JavascriptInterface public String randomBytes(int size) {
            byte[] b = new byte[Math.max(1, Math.min(4096, size))];
            random.nextBytes(b);
            return byteArrayJson(b);
        }

        @JavascriptInterface public String bufferFrom(String value, String encoding) {
            try { return byteArrayJson(decodeString(value, encoding)); }
            catch (Exception e) { return "[]"; }
        }

        @JavascriptInterface public String bufferToString(String bytesJson, String format) {
            try {
                byte[] bytes = parseByteArray(bytesJson);
                String f = format == null ? "utf8" : format.toLowerCase(java.util.Locale.ROOT);
                if ("base64".equals(f)) return Base64.encodeToString(bytes, Base64.NO_WRAP);
                if ("hex".equals(f)) {
                    StringBuilder b = new StringBuilder();
                    for (byte x : bytes) b.append(String.format(java.util.Locale.ROOT, "%02x", x & 0xff));
                    return b.toString();
                }
                return new String(bytes, StandardCharsets.UTF_8);
            } catch (Exception e) { return ""; }
        }

        @JavascriptInterface public String aesEncrypt(String dataJson, String mode, String keyJson, String ivJson) {
            try {
                byte[] data = parseByteArray(dataJson);
                byte[] key = normalizeKey(parseByteArray(keyJson), 16);
                byte[] iv = normalizeKey(parseByteArray(ivJson), 16);
                String lower = mode == null ? "" : mode.toLowerCase(java.util.Locale.ROOT);
                String transformation = lower.contains("ecb") ? "AES/ECB/NoPadding" : "AES/CBC/PKCS5Padding";
                Cipher cipher = Cipher.getInstance(transformation);
                SecretKeySpec sk = new SecretKeySpec(key, "AES");
                if (transformation.contains("ECB")) cipher.init(Cipher.ENCRYPT_MODE, sk);
                else cipher.init(Cipher.ENCRYPT_MODE, sk, new IvParameterSpec(iv));
                return byteArrayJson(cipher.doFinal(data));
            } catch (Exception e) { return "[]"; }
        }

        @JavascriptInterface public String rsaEncrypt(String dataJson, String publicKeyText) {
            try {
                byte[] data = parseByteArray(dataJson);
                String clean = publicKeyText == null ? "" : publicKeyText
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s+", "");
                byte[] der = Base64.decode(clean, Base64.DEFAULT);
                java.security.PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
                Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key);
                return byteArrayJson(cipher.doFinal(data));
            } catch (Exception e) { return "[]"; }
        }

        private static byte[] decodeString(String value, String encoding) {
            String enc = encoding == null ? "utf8" : encoding.toLowerCase(java.util.Locale.ROOT);
            if ("base64".equals(enc)) return Base64.decode(value == null ? "" : value, Base64.DEFAULT);
            if ("hex".equals(enc)) {
                String s = value == null ? "" : value.replaceAll("\\s+", "");
                byte[] out = new byte[s.length() / 2];
                for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
                return out;
            }
            return (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        }

        private static byte[] parseByteArray(String json) throws Exception {
            JSONArray a = new JSONArray(json == null || json.isEmpty() ? "[]" : json);
            byte[] out = new byte[a.length()];
            for (int i = 0; i < a.length(); i++) out[i] = (byte) (a.optInt(i, 0) & 0xff);
            return out;
        }

        private static byte[] normalizeKey(byte[] in, int size) {
            byte[] out = new byte[size];
            if (in != null) System.arraycopy(in, 0, out, 0, Math.min(in.length, out.length));
            return out;
        }

        private static String byteArrayJson(byte[] b) {
            JSONArray a = new JSONArray();
            if (b != null) for (byte x : b) a.put(x & 0xff);
            return a.toString();
        }
    }

    private void completeHttp(String id, String error, JSONObject response, Object body) {
        String bodyJs;
        if (body == null) bodyJs = "null";
        else if (body instanceof JSONObject || body instanceof JSONArray) bodyJs = body.toString();
        else bodyJs = JSONObject.quote(String.valueOf(body));
        String js = "globalThis.__lxHttpComplete(" + JSONObject.quote(id) + ","
                + (error == null ? "null" : JSONObject.quote(error)) + ","
                + (response == null ? "null" : response.toString()) + "," + bodyJs + ");";
        main.post(() -> {
            WebView w = webView;
            if (w != null && !destroyed) try { w.evaluateJavascript(js, null); } catch (Exception ignored) { }
        });
    }

    private static JSONArray byteArrayJsonArray(byte[] bytes) {
        JSONArray a = new JSONArray();
        if (bytes != null) for (byte x : bytes) a.put(x & 0xff);
        return a;
    }

    private static Object decodeBody(String body) {
        String t = body == null ? "" : body.trim();
        if (t.isEmpty()) return "";
        try {
            Object value = new JSONTokener(t).nextValue();
            if (value instanceof JSONObject || value instanceof JSONArray) return value;
        } catch (Exception ignored) { }
        return body == null ? "" : body;
    }

    private static Map<String,String> jsonHeaders(JSONObject o) {
        Map<String,String> out = new LinkedHashMap<>();
        if (o == null) return out;
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            Object value = o.opt(k);
            String v = value == null || value == JSONObject.NULL ? "" : String.valueOf(value);
            if (!k.isEmpty() && !v.isEmpty()) out.put(k, v);
        }
        return out;
    }

    private static String scriptMeta(String raw, String key) {
        if (raw == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("@" + java.util.regex.Pattern.quote(key) + "\\s+([^\\r\\n*]+)").matcher(raw);
        return m.find() ? m.group(1).trim() : "";
    }

    private String normalizeSourceError(String m) {
        String s = m == null ? "" : m.trim();
        if (s.isEmpty()) return provider.label + " 未知错误";
        return provider.label + "：" + s;
    }

    private static String safe(Exception e) { return e == null || e.getMessage() == null ? "unknown" : e.getMessage(); }

    public void destroy() {
        destroyed = true;
        for (CompletableFuture<String> f : pending.values()) f.completeExceptionally(new Exception("runtime destroyed"));
        pending.clear();
        worker.shutdownNow();
        main.post(() -> {
            WebView w = webView; webView = null;
            if (w != null) try { w.destroy(); } catch (Exception ignored) { }
        });
    }
}
