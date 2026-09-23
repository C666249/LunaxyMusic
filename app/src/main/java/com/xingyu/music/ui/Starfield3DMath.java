package com.xingyu.music.ui;

/**
 * Small dependency-free quaternion helper used by {@link Starfield3DView}.
 * Quaternion layout is [w, x, y, z].  Keeping the math outside Android sensor
 * classes makes the 360-degree camera transform independently testable.
 */
final class Starfield3DMath {
    private Starfield3DMath() { }

    static void identity(float[] q) {
        q[0] = 1f; q[1] = 0f; q[2] = 0f; q[3] = 0f;
    }

    static void copy(float[] src, float[] dst) {
        dst[0] = src[0]; dst[1] = src[1]; dst[2] = src[2]; dst[3] = src[3];
    }

    static void normalize(float[] q) {
        float n = (float) Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        if (n < 1.0e-7f) {
            identity(q);
            return;
        }
        float inv = 1f / n;
        q[0] *= inv; q[1] *= inv; q[2] *= inv; q[3] *= inv;
    }

    /**
     * Camera-space rotation relative to the pose captured when the player opened.
     * Android rotation-vector quaternions map device -> world, therefore a world
     * point represented in baseline device coordinates reaches current device
     * coordinates through inverse(current) * baseline.
     */
    static void relativeCamera(float[] baselineDeviceToWorld, float[] currentDeviceToWorld, float[] out) {
        float aw = currentDeviceToWorld[0];
        float ax = -currentDeviceToWorld[1];
        float ay = -currentDeviceToWorld[2];
        float az = -currentDeviceToWorld[3];
        multiply(aw, ax, ay, az,
                baselineDeviceToWorld[0], baselineDeviceToWorld[1], baselineDeviceToWorld[2], baselineDeviceToWorld[3], out);
        normalize(out);
    }

    static void multiply(float aw, float ax, float ay, float az,
                         float bw, float bx, float by, float bz, float[] out) {
        out[0] = aw * bw - ax * bx - ay * by - az * bz;
        out[1] = aw * bx + ax * bw + ay * bz - az * by;
        out[2] = aw * by - ax * bz + ay * bw + az * bx;
        out[3] = aw * bz + ax * by - ay * bx + az * bw;
    }

    static void slerp(float[] from, float[] to, float t, float[] out) {
        t = clamp(t, 0f, 1f);
        float tw = to[0], tx = to[1], ty = to[2], tz = to[3];
        float dot = from[0] * tw + from[1] * tx + from[2] * ty + from[3] * tz;
        // q and -q represent the same orientation.  Pick the sign that preserves
        // the shortest continuous path so crossing 180 degrees does not jump.
        if (dot < 0f) {
            dot = -dot;
            tw = -tw; tx = -tx; ty = -ty; tz = -tz;
        }
        if (dot > .9995f) {
            out[0] = from[0] + t * (tw - from[0]);
            out[1] = from[1] + t * (tx - from[1]);
            out[2] = from[2] + t * (ty - from[2]);
            out[3] = from[3] + t * (tz - from[3]);
            normalize(out);
            return;
        }
        dot = clamp(dot, -1f, 1f);
        double theta0 = Math.acos(dot);
        double sin0 = Math.sin(theta0);
        if (Math.abs(sin0) < 1.0e-8) {
            copy(from, out);
            return;
        }
        double theta = theta0 * t;
        double s0 = Math.cos(theta) - dot * Math.sin(theta) / sin0;
        double s1 = Math.sin(theta) / sin0;
        out[0] = (float) (s0 * from[0] + s1 * tw);
        out[1] = (float) (s0 * from[1] + s1 * tx);
        out[2] = (float) (s0 * from[2] + s1 * ty);
        out[3] = (float) (s0 * from[3] + s1 * tz);
        normalize(out);
    }

    static void rotateVector(float[] q, float x, float y, float z, float[] out3) {
        // Optimized q * v * inverse(q).
        float qw = q[0], qx = q[1], qy = q[2], qz = q[3];
        float tx = 2f * (qy * z - qz * y);
        float ty = 2f * (qz * x - qx * z);
        float tz = 2f * (qx * y - qy * x);
        out3[0] = x + qw * tx + (qy * tz - qz * ty);
        out3[1] = y + qw * ty + (qz * tx - qx * tz);
        out3[2] = z + qw * tz + (qx * ty - qy * tx);
    }

    static float angularDistance(float[] a, float[] b) {
        float dot = Math.abs(a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]);
        dot = clamp(dot, 0f, 1f);
        return 2f * (float) Math.acos(dot);
    }

    static void axisAngle(float ax, float ay, float az, float angle, float[] out) {
        float n = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (n < 1.0e-7f) {
            identity(out);
            return;
        }
        float half = angle * .5f;
        float s = (float) Math.sin(half) / n;
        out[0] = (float) Math.cos(half);
        out[1] = ax * s;
        out[2] = ay * s;
        out[3] = az * s;
        normalize(out);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
