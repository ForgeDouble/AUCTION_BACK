package com.example.auction.push.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkPushService {
    private final FcmService fcmService;

    // 대량 발송 관련
    @Async("pushExecutor")
    public CompletableFuture<Integer> sendAll(List<String> tokens, String title, String body, Map<String,String> data) {
        if (tokens == null || tokens.isEmpty())
            return CompletableFuture.completedFuture(0);

        List<List<String>> chunks = partition(tokens, 500); // 500개의 병렬 구조
        int workers = Math.min(20, chunks.size());
        log.info("[FCM] bulk chunks = {}, workers = {}", chunks.size(), workers);

        ExecutorService pool = Executors.newFixedThreadPool(workers);
        List<CompletableFuture<Integer>> futures = new ArrayList<>();

        for (List<String> c : chunks) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return fcmService.sendMulticastWithRetry(c, title, body, data).getSuccessCount();
                } catch (Exception e) {
                    log.error("[FCM] 청크 발송 에러 : {}", e.toString());
                    return 0;
                }
            }, pool));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    pool.shutdown();
                    int total = futures.stream().mapToInt(CompletableFuture::join).sum();
                    log.info("[FCM] 벌크 전부 성공 ={}", total);
                    return total;
                });
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) out.add(list.subList(i, Math.min(i + size, list.size())));
        return out;
    }
}
