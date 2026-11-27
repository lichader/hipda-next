package com.lichader.hipdanext.okhttp;

import androidx.annotation.NonNull;

import com.lichader.hipdanext.di.SingletonEntryPoint;
import com.lichader.hipdanext.utils.HiUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.Dns;

/** System DNS with an in-memory cache for forum hosts. */
@Singleton
public class CachedDns implements Dns {

    // OkHttp resolves hosts on several threads at once.
    private final Map<String, List<InetAddress>> mCache = new ConcurrentHashMap<>();

    @Inject
    public CachedDns() {
    }

    /**
     * @deprecated inject {@link CachedDns} instead.
     */
    @Deprecated
    public static CachedDns getInstance() {
        return SingletonEntryPoint.get().cachedDns();
    }

    @NonNull
    @Override
    public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
        List<InetAddress> ips = mCache.get(hostname);
        if (ips == null) {
            ips = Dns.SYSTEM.lookup(hostname);
            if (hostname.endsWith(HiUtils.CookieDomain) && ips.size() > 0)
                mCache.put(hostname, ips);
        }
        return ips;
    }

    public void clearCache() {
        mCache.clear();
    }

}
