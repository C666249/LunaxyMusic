package com.xingyu.music.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.xingyu.music.model.ImportedPlaylist;
import com.xingyu.music.model.Song;
import com.xingyu.music.model.SourceVariant;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class LibraryStore {
    private final SharedPreferences prefs;
    private final PersonalizationStore personalization;
    private final Object playlistCacheLock = new Object();
    private String playlistCacheRaw = null;
    private List<ImportedPlaylist> playlistCache = null;
    public LibraryStore(Context c){ this(c, new PersonalizationStore(c)); }
    public LibraryStore(Context c, PersonalizationStore personalization){
        prefs=c.getSharedPreferences("xingyu_library_v3", Context.MODE_PRIVATE);
        this.personalization=personalization==null?new PersonalizationStore(c):personalization;
    }

    public List<Song> favorites(){ return decodeSongs(prefs.getString("favorites","[]")); }
    public List<Song> history(){
        List<Song> all=decodeSongs(prefs.getString("history","[]"));
        return all.size()<=50?all:new ArrayList<>(all.subList(0,50));
    }
    public List<ImportedPlaylist> playlists(){
        String raw = prefs.getString("playlists", "[]");
        synchronized (playlistCacheLock) {
            if (playlistCache != null && raw.equals(playlistCacheRaw)) return copyPlaylists(playlistCache);
        }
        List<ImportedPlaylist> decoded = decodePlaylists(raw);
        synchronized (playlistCacheLock) {
            playlistCacheRaw = raw;
            playlistCache = decoded;
            return copyPlaylists(decoded);
        }
    }
    public List<String> searchHistory(){ return decodeStrings(prefs.getString("search_history","[]")); }

    public boolean isFavorite(Song song){ for(Song s:favorites()) if(s.key().equals(song.key())) return true; return false; }
    public List<Song> toggleFavorite(Song song){
        List<Song> list=favorites(); int found=-1;
        for(int i=0;i<list.size();i++) if(list.get(i).key().equals(song.key())){found=i;break;}
        if(found>=0){
            list.remove(found);
            personalization.recordLibraryEvent(song, PersonalizationStore.FAVORITE_REMOVE);
        } else {
            list.add(0,song);
            personalization.recordLibraryEvent(song, PersonalizationStore.FAVORITE_ADD);
        }
        saveSongs("favorites",list); return list;
    }

    /** Persist an explicit manual order for Favorites. UI-only/library state; playback routing is untouched. */
    public List<Song> setFavoriteOrder(List<Song> ordered){
        List<Song> out=new ArrayList<>();
        if(ordered!=null) for(Song s:ordered) if(s!=null) out.add(s);
        saveSongs("favorites",out);
        return out;
    }

    /** Remove multiple favorites by stable Song.key(). */
    public List<Song> removeFavorites(List<String> keys){
        Set<String> remove=new HashSet<>(); if(keys!=null) remove.addAll(keys);
        List<Song> out=new ArrayList<>();
        for(Song s:favorites()){
            if(s==null) continue;
            if(remove.contains(s.key())) personalization.recordLibraryEvent(s, PersonalizationStore.FAVORITE_REMOVE);
            else out.add(s);
        }
        saveSongs("favorites",out); return out;
    }

    public List<Song> addHistory(Song song){
        List<Song> src=history(), out=new ArrayList<>();
        out.add(song);
        // Home still shows a compact shelf, but the full recent collection keeps up to 50 tracks.
        for(Song s:src) if(!s.key().equals(song.key())&&out.size()<50) out.add(s);
        saveSongs("history",out); return out;
    }
    public List<String> addSearchHistory(String query){
        String clean=query==null?"":query.trim();
        List<String> src=searchHistory(), out=new ArrayList<>();
        if(!clean.isEmpty()) out.add(clean);
        for(String q:src) if(q!=null&&!q.trim().isEmpty()&&!q.equalsIgnoreCase(clean)&&out.size()<16) out.add(q);
        saveStrings("search_history",out); return out;
    }
    public List<String> clearSearchHistory(){ prefs.edit().remove("search_history").apply(); return new ArrayList<>(); }

    public List<ImportedPlaylist> savePlaylist(ImportedPlaylist p){
        List<ImportedPlaylist> src=playlists(), out=new ArrayList<>();
        out.add(p);
        for(ImportedPlaylist x:src) if(!x.id.equals(p.id)) out.add(x);
        savePlaylists(out); return out;
    }

    public List<ImportedPlaylist> removePlaylist(String id){
        List<ImportedPlaylist> out=new ArrayList<>();
        for(ImportedPlaylist p:playlists()) if(!p.id.equals(id)) out.add(p);
        savePlaylists(out); return out;
    }

    /** Batch delete used by the playlist management sheet. */
    public List<ImportedPlaylist> removePlaylists(List<String> ids){
        Set<String> remove=new HashSet<>();
        if(ids!=null) remove.addAll(ids);
        List<ImportedPlaylist> out=new ArrayList<>();
        for(ImportedPlaylist p:playlists()) if(!remove.contains(p.id)) out.add(p);
        savePlaylists(out); return out;
    }

    public List<ImportedPlaylist> createPlaylist(String name){
        String clean=name==null?"":name.trim(); if(clean.isEmpty()) clean="新建歌单";
        ImportedPlaylist p=new ImportedPlaylist("local:"+System.currentTimeMillis(),clean,"local",new ArrayList<>());
        return savePlaylist(p);
    }

    public List<ImportedPlaylist> addSongToPlaylist(String id,Song song){
        List<ImportedPlaylist> src=playlists(), out=new ArrayList<>();
        for(ImportedPlaylist p:src){
            if(p.id.equals(id)){
                List<Song> songs=new ArrayList<>(p.songs); boolean exists=false;
                for(Song s:songs) if(s.key().equals(song.key())){exists=true;break;}
                // Newly added songs belong at the top of a saved playlist. Existing songs keep position.
                if(!exists) {
                    songs.add(0,song);
                    personalization.recordLibraryEvent(song, PersonalizationStore.PLAYLIST_ADD);
                }
                out.add(new ImportedPlaylist(p.id,p.name,p.source,songs,p.importedAt));
            } else out.add(p);
        }
        savePlaylists(out); return out;
    }

    public List<ImportedPlaylist> addSongsToPlaylist(String id,List<Song> incoming){
        List<ImportedPlaylist> src=playlists(), out=new ArrayList<>();
        for(ImportedPlaylist p:src){
            if(p.id.equals(id)){
                List<Song> songs=new ArrayList<>();
                Set<String> existingKeys=new HashSet<>();
                for(Song current:p.songs) if(current!=null) existingKeys.add(current.key());
                Set<String> addedKeys=new HashSet<>();
                // Preserve the user's picker order while placing only genuinely new songs first.
                if(incoming!=null){
                    for(Song candidate:incoming){
                        if(candidate==null||existingKeys.contains(candidate.key())||!addedKeys.add(candidate.key())) continue;
                        songs.add(candidate);
                        personalization.recordLibraryEvent(candidate, PersonalizationStore.PLAYLIST_ADD);
                    }
                }
                songs.addAll(p.songs);
                out.add(new ImportedPlaylist(p.id,p.name,p.source,songs,p.importedAt));
            } else out.add(p);
        }
        savePlaylists(out); return out;
    }

    public List<ImportedPlaylist> renamePlaylist(String id,String newName){
        String clean=newName==null?"":newName.trim();
        if(id==null||clean.isEmpty()) return playlists();
        List<ImportedPlaylist> src=playlists(), out=new ArrayList<>();
        for(ImportedPlaylist p:src){
            if(p.id.equals(id)) out.add(new ImportedPlaylist(p.id,clean,p.source,p.songs,p.importedAt));
            else out.add(p);
        }
        savePlaylists(out); return out;
    }

    /** Persist an explicit manual order for one saved playlist, regardless of import source. */
    public List<ImportedPlaylist> setPlaylistSongOrder(String id,List<Song> ordered){
        if(id==null) return playlists();
        List<ImportedPlaylist> src=playlists(), out=new ArrayList<>();
        for(ImportedPlaylist p:src){
            if(p.id.equals(id)){
                List<Song> songs=new ArrayList<>();
                if(ordered!=null) for(Song s:ordered) if(s!=null) songs.add(s);
                out.add(new ImportedPlaylist(p.id,p.name,p.source,songs,p.importedAt));
            } else out.add(p);
        }
        savePlaylists(out);
        return out;
    }

    /** Remove multiple songs from one locally stored playlist copy. */
    public List<ImportedPlaylist> removeSongsFromPlaylist(String id,List<String> keys){
        Set<String> remove=new HashSet<>(); if(keys!=null) remove.addAll(keys);
        List<ImportedPlaylist> src=playlists(), out=new ArrayList<>();
        for(ImportedPlaylist p:src){
            if(p.id.equals(id)){
                List<Song> songs=new ArrayList<>();
                for(Song s:p.songs) {
                    if(s==null) continue;
                    if(remove.contains(s.key())) personalization.recordLibraryEvent(s, PersonalizationStore.PLAYLIST_REMOVE);
                    else songs.add(s);
                }
                out.add(new ImportedPlaylist(p.id,p.name,p.source,songs,p.importedAt));
            } else out.add(p);
        }
        savePlaylists(out); return out;
    }

    /**
     * Merge two playlists into a new local playlist while preserving both originals.
     * Duplicate canonical tracks are collapsed. Provider identities are combined so the
     * resolver can still choose the healthy route. When two variants are otherwise equal, NetEase (wy) is preferred so the merged
     * canonical song keeps the provider identity that can supply synced lyrics. 320k
     * support still outranks 128k-only support, and all fallback provider identities remain.
     */
    public List<ImportedPlaylist> mergePlaylists(String firstId,String secondId,String mergedName){
        if(firstId==null||secondId==null||firstId.equals(secondId)) return playlists();
        ImportedPlaylist first=null, second=null;
        List<ImportedPlaylist> src=playlists();
        for(ImportedPlaylist p:src){
            if(firstId.equals(p.id)) first=p;
            if(secondId.equals(p.id)) second=p;
        }
        if(first==null||second==null) return src;

        LinkedHashMap<String,Song> unique=new LinkedHashMap<>();
        mergeInto(unique,first.songs);
        mergeInto(unique,second.songs);
        String name=mergedName==null?"":mergedName.trim();
        if(name.isEmpty()) name=first.name+" + "+second.name;
        ImportedPlaylist merged=new ImportedPlaylist(
                "local:merge:"+System.currentTimeMillis(),name,"local",new ArrayList<>(unique.values()));
        List<ImportedPlaylist> out=new ArrayList<>();
        out.add(merged);
        out.addAll(src);
        savePlaylists(out);
        return out;
    }

    private static void mergeInto(Map<String,Song> out,List<Song> songs){
        if(songs==null) return;
        for(Song candidate:songs){
            if(candidate==null) continue;
            String id=playlistIdentity(candidate);
            Song old=out.get(id);
            if(old==null) out.put(id,candidate);
            else {
                Song combined=old.withVariant(candidate);
                String preferred=bestSource(combined);
                out.put(id,combined.preferSource(preferred));
            }
        }
    }

    private static String bestSource(Song song){
        String best=""; int bestScore=Integer.MIN_VALUE;
        for(SourceVariant v:song.variants()){
            int score=(v.quality320?200:(v.quality128?100:0));
            // Tie-break: NetEase > QQ > others. This does not discard fallbacks.
            if("wy".equals(v.source)) score+=9;
            else if("tx".equals(v.source)) score+=8;
            else if("kw".equals(v.source)) score+=5;
            else if("kg".equals(v.source)) score+=4;
            else if("mg".equals(v.source)) score+=3;
            if(score>bestScore){ bestScore=score; best=v.source; }
        }
        return best.isEmpty()?song.source:best;
    }

    private static String playlistIdentity(Song song){
        return normalize(song.title)+"|"+normalize(song.artist);
    }

    private static String normalize(String text){
        return (text==null?"":text.toLowerCase(Locale.ROOT))
                .replaceAll("[\\s\\p{Punct}，。！？：；、“”‘’《》【】（）()]+","");
    }

    private void saveSongs(String key,List<Song> list){ JSONArray a=new JSONArray(); for(Song s:list)a.put(s.toJson()); prefs.edit().putString(key,a.toString()).apply(); }
    private List<Song> decodeSongs(String raw){ List<Song> out=new ArrayList<>(); try{JSONArray a=new JSONArray(raw==null?"[]":raw); for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i); if(o!=null)out.add(Song.fromJson(o));}}catch(Exception ignored){} return out; }
    private void saveStrings(String key,List<String> list){ JSONArray a=new JSONArray(); for(String x:list) if(x!=null&&!x.trim().isEmpty()) a.put(x.trim()); prefs.edit().putString(key,a.toString()).apply(); }
    private List<String> decodeStrings(String raw){ List<String> out=new ArrayList<>(); try{JSONArray a=new JSONArray(raw==null?"[]":raw); for(int i=0;i<a.length();i++){String x=a.optString(i,"").trim(); if(!x.isEmpty()) out.add(x);}}catch(Exception ignored){} return out; }
    private void savePlaylists(List<ImportedPlaylist> list){
        JSONArray a=new JSONArray(); for(ImportedPlaylist p:list)a.put(p.toJson());
        String raw=a.toString();
        synchronized (playlistCacheLock) { playlistCacheRaw=raw; playlistCache=copyPlaylists(list); }
        prefs.edit().putString("playlists",raw).apply();
    }
    private static List<ImportedPlaylist> copyPlaylists(List<ImportedPlaylist> source){
        List<ImportedPlaylist> out=new ArrayList<>();
        if(source!=null) for(ImportedPlaylist p:source) if(p!=null)
            out.add(new ImportedPlaylist(p.id,p.name,p.source,p.songs,p.importedAt));
        return out;
    }
    private List<ImportedPlaylist> decodePlaylists(String raw){List<ImportedPlaylist> out=new ArrayList<>(); try{JSONArray a=new JSONArray(raw==null?"[]":raw); for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)out.add(ImportedPlaylist.fromJson(o));}}catch(Exception ignored){}return out;}
}
