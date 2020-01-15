# 给nginx服务器添加接口缓存

```
proxy_cache_path /var/log/cache levels=1:2 keys_zone=mycache:10m max_size=16m inactive=60m use_temp_path=off;

location ~ ^/abc/bcd {
        proxy_cache mycache;
        proxy_cache_key $host$uri$is_args$args$http_custom_header;
        proxy_cache_use_stale error timeout updating http_500 http_502 http_503 http_504;
        proxy_cache_valid 200 1m;
        proxy_cache_lock on;
        proxy_cache_lock_timeout 3s;
        add_header  Nginx-Cache "$upstream_cache_status";
        proxy_pass http://127.0.0.1:666;
    }

    location /abc {
        proxy_pass   http://127.0.0.1:6666;
    }
```
