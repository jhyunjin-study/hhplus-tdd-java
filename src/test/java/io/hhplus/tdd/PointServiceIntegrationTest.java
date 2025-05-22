package io.hhplus.tdd;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PointServiceIntegrationTest {

    private PointService pointService;

    @BeforeEach
    void setUp() {
        // 테스트 실행 전에 PointService 초기화
        UserPointTable userPointTable = new UserPointTable();
        PointHistoryTable pointHistoryTable = new PointHistoryTable();
        pointService = new PointService(userPointTable, pointHistoryTable);
    }

    // 포인트 충전 시 정상 동작 여부 확인
    @Test
    void testChargeWithinLimit() {
        long userId = 1L;
        int chargeAmount = 5000;
        String message = "충전 금액이 예상과 일치하지 않습니다.";

        System.out.println("🚀테스트 시작: testChargeWithinLimit");
        // 포인트 충전
        UserPoint result = pointService.charge(userId, chargeAmount);
        System.out.println("🚀테스트 종료: testChargeWithinLimit");
        System.out.println("🚀[로그:정현진] 충전금액이 5000원인지 확인: " + result.point());

        assertEquals(chargeAmount, result.point(), message);
    }

    // 포인트 충전 시 최대 잔고 초과 여부 확인
    @Test
    void testChargeExceedingMaxBalance() {
        long userId = 1L;
        int chargeAmount1 = 9000;
        int chargeAmount2 = 2000;
        String message = "최대 잔고는 10,000원을 초과할 수 없습니다.";

        System.out.println("🚀테스트 시작: testChargeExceedingMaxBalance");
        // 첫 번째 충전
        pointService.charge(userId, chargeAmount1);

        // 두 번째 충전 시 예외 발생 여부 확인
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            pointService.charge(userId, chargeAmount2);
        });
        System.out.println("🚀테스트 종료: testChargeExceedingMaxBalance");
        System.out.println("🚀[로그:정현진] 충전금액이 2000원인지 확인: " + exception.getMessage());

        assertEquals(message, exception.getMessage());
    }

    // 포인트 사용 시 잔고 부족 여부 확인
    @Test
    void testUseWithInsufficientBalance() {
        long userId = 1L;
        int chargeAmount = 1000;
        int useAmount = 2000;
        String message = "잔고가 부족합니다.";

        System.out.println("🚀테스트 시작: testUseWithInsufficientBalance");
        // 포인트 충전
        pointService.charge(userId, chargeAmount);

        // 포인트 사용 시 예외 발생 여부 확인
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            pointService.use(userId, useAmount);
        });
        System.out.println("🚀테스트 종료: testUseWithInsufficientBalance");
        if(exception.getMessage().equals(message)) {
            System.out.println("🚀결과 : " + exception.getMessage());
        }

        assertEquals(message, exception.getMessage());
    }

    // 충전 요청 중 예외가 발생했을 때, 트랜잭션이 롤백되는지 확인하는 테스트
    @Test
    @DisplayName("실패 시 트랜잭션 롤백 테스트 - 잘못된 금액")
    void testTransactionRollbackOnFailure() throws InterruptedException {
        long userId = 4L;
        long initialBalance = 5000L;
        long problematicAmount = -1000L; // 예외를 유도할 음수 금액

        // 1. 초기 사용자 포인트 설정
        pointService.charge(userId, initialBalance);
        System.out.println("\n🚀[테스트 시작] testTransactionRollbackOnFailure - 사용자 " + userId + " 초기 잔고: " + initialBalance);

        // 초기 잔고가 정확히 설정되었는지 확인 (테스트 전제 조건)
        UserPoint userAfterSetup = pointService.getUserPoint(userId);
        assertNotNull(userAfterSetup, "초기 사용자 설정 후 UserPoint가 null이면 안됩니다.");
        assertEquals(initialBalance, userAfterSetup.point(), "초기 잔고 설정이 잘못되었습니다.");

        ExecutorService executor = Executors.newSingleThreadExecutor();

        // 2. 비동기적으로 실패를 유도하는 charge() 호출
        Future<Void> future = executor.submit(() -> {
            try {
                pointService.charge(userId, problematicAmount);
                fail("charge() 메소드가 유효하지 않은 금액(" + problematicAmount + ")에 대해 예상된 예외를 던지지 않고 성공했습니다.");
            } catch (IllegalArgumentException e) {
                System.out.println("🚀[로그] 사용자 " + userId + ": 예상된 IllegalArgumentException 발생 - " + e.getMessage());
            } catch (Exception e) {
                fail("사용자 " + userId + ": 트랜잭션 롤백 테스트 중 예상치 못한 다른 예외 발생: " + e.getMessage(), e);
            }
            return null;
        });

        // 3. 비동기 작업 완료 대기 및 발생 가능한 ExecutionException 처리
        try {
            future.get();
        } catch (ExecutionException e) {
            fail("비동기 작업 실행 중 ExecutionException 발생: " + e.getCause().getMessage(), e.getCause());
        } finally {
            // 4. ExecutorService 종료
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("경고: ExecutorService가 지정된 시간 내에 종료되지 않았습니다.");
            }
        }

        // 5. 최종 잔고 확인 및 롤백 검증
        UserPoint result = pointService.getUserPoint(userId);
        assertNotNull(result, "롤백 후 UserPoint 객체가 null이면 안됩니다.");
        System.out.println("🚀[테스트 종료] testTransactionRollbackOnFailure - 사용자 " + userId + " 최종 잔고: " + result.point());

        assertEquals(initialBalance, result.point(),
                "예외 발생 시 사용자 " + userId + "의 잔고가 초기 상태(" + initialBalance + ")로 롤백되지 않았습니다. 현재 잔고: " + result.point());
    }

// ========================== 아래부터 동시성 테스트 ==========================

    // 포인트 충전 기능 동시성 테스트 : 최대잔고까지 충전
    @Test
    void testConcurrentChargesAreSynchronized() throws InterruptedException, ExecutionException {
        long userId = 2L; // 테스트할 사용자 ID를 설정
        int chargeAmount = 1000; // 충전할 금액을 설정
        int maxBalance = 10000; // 최대 잔고를 설정
        int threadCount = 10; // 스레드 개수를 설정
        // 타이머 시작
        System.out.println("🚀테스트 시작: testConcurrentChargesAreSynchronized"); // 테스트 시작 로그 출력
        ExecutorService executor = Executors.newFixedThreadPool(threadCount); // 10개의 스레드를 가진 스레드 풀 생성
        List<Callable<Void>> tasks = new ArrayList<>(); // 실행할 작업 목록을 저장할 리스트 생성
        long startTime = System.currentTimeMillis(); // 현재 시간을 시작 시간으로 설정
        // 10개의 충전 작업 추가
        for (int i = 0; i < threadCount; i++) { // 10번 반복하여 작업 추가
            tasks.add(() -> { // Callable 작업 추가
            try {
                pointService.charge(userId, chargeAmount); // 사용자 포인트를 1000만큼 충전
            } catch (Exception ignored) { // 예외 발생 시 무시
            }
            return null; // Callable<Void>이므로 null 반환
            });
        }
        // 모든 작업 실행
        List<Future<Void>> futures = executor.invokeAll(tasks); // 스레드 풀에서 모든 작업을 병렬로 실행

        for (Future<Void> f : futures) { // 각 작업의 결과를 기다림
            f.get(); // 작업이 완료될 때까지 대기
        }
        // 타이머 종료
        long endTime = System.currentTimeMillis(); // 현재 시간을 종료 시간으로 설정
        System.out.println("🚀[로그:정현진] startTime: " + startTime); // 시작 시간 로그 출력
        System.out.println("🚀[로그:정현진] endTime: " + endTime); // 시작 시간 로그 출력
        // 소요 시간 계산
        System.out.println("🚀소요 시간: " + (endTime - startTime) + "ms"); // 소요 시간 출력
        executor.shutdown(); // 스레드 풀 종료

        // 최종 잔고 확인
        UserPoint result = pointService.getUserPoint(userId); // 사용자 포인트 정보를 가져옴
        System.out.println("🚀테스트 종료: testConcurrentChargesAreSynchronized"); // 테스트 종료 로그 출력
        if(result.point() == maxBalance) {
            System.out.println("🚀결과 : 포인트 충전 기능 동시성 테스트 성공");
        }

        assertEquals(maxBalance, result.point(), "최종 잔고가 기대와 다릅니다."); // 최종 잔고가 10000인지 확인
    }

    // 포인트 사용 기능 동시성 테스트
    @Test
    void testConcurrentUseIsSynchronized() throws InterruptedException, ExecutionException {
        long userId = 3L;
        int chargeAmount = 10000;
        int useAmount = 1000;
        pointService.charge(userId, chargeAmount);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Callable<Void>> tasks = new ArrayList<>();

        System.out.println("🚀테스트 시작: testConcurrentUseIsSynchronized");
        // 10개의 사용 작업 추가
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> {
                try {
                    pointService.use(userId, useAmount);
                } catch (Exception ignored) {
                }
                return null;
            });
        }

        // 모든 작업 실행
        List<Future<Void>> futures = executor.invokeAll(tasks);
        for (Future<Void> f : futures) {
            f.get();
        }
        executor.shutdown();

        // 최종 잔고 확인
        UserPoint result = pointService.getUserPoint(userId);
        System.out.println("🚀테스트 종료: testConcurrentUseIsSynchronized");
        System.out.println("🚀[로그:정현진] result.point(): " + result.point());
        assertEquals(0, result.point(), "모든 포인트가 정상적으로 차감되었는지 확인하세요.");
    }

    // 포인트 충전 시 최대 잔고 초과 여부 확인
    @Test
    void testMaxBalanceIsNotExceededAndExceptionCount() throws InterruptedException {
        long userId = 1L;
        int chargeAmount = 1000;
        int threadCount = 20;
        int maxBalance = 10000;
        int expectedSuccessCount = 10; // 1000 * 10 = 10000 이므로 10번 성공 예상, 숫자를 수정하여 예외 발생 횟수를 조정할 수 있습니다.
        int expectedExceptionCount = threadCount - expectedSuccessCount; // 20 - 10 = 10번 예외 예상

        AtomicInteger actualSuccessCount = new AtomicInteger(0);
        AtomicInteger actualExceptionCount = new AtomicInteger(0);

        System.out.println("🚀테스트 시작: testMaxBalanceIsNotExceededAndExceptionCount");

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                try {
                    pointService.charge(userId, chargeAmount);
                    actualSuccessCount.incrementAndGet(); // 성공 카운트 증가
                } catch (IllegalArgumentException e) {
                    // 예상되는 예외 (최대 잔고 초과)는 카운트하고 무시
                    actualExceptionCount.incrementAndGet(); // 예외 카운트 증가
                } catch (Exception e) {
                    // 예상치 못한 다른 예외는 즉시 테스트 실패
                    Assertions.fail("예상치 못한 예외 발생: " + e.getMessage());
                }
                return null;
            });
        }

        List<Future<Void>> futures = executor.invokeAll(tasks);

        for (Future<Void> f : futures) {
            try {
                f.get(); // 예외 발생 시 ExecutionException 던짐
            } catch (ExecutionException e) {
                Assertions.fail("하나 이상의 작업에서 예외 발생: " + e.getCause().getMessage(), e.getCause());
            }
        }
        executor.shutdown();

        System.out.println("🚀[로그] 성공 충전 시도 횟수: " + actualSuccessCount.get());
        System.out.println("🚀[로그] 예외 발생 횟수: " + actualExceptionCount.get());

        UserPoint result = pointService.getUserPoint(userId);
        System.out.println("🚀테스트 종료: testMaxBalanceIsNotExceededAndExceptionCount");
        System.out.println("🚀[로그] 최종 잔고: " + result.point());

        // 최종 잔고가 예상과 일치하는지 검증
        assertEquals(maxBalance, result.point(), "최종 잔고는 최대 잔고와 같아야 합니다.");
        // 성공 및 실패 횟수가 예상과 일치하는지 검증
        assertEquals(expectedSuccessCount, actualSuccessCount.get(), "예상 성공 횟수가 일치해야 합니다.");
        assertEquals(expectedExceptionCount, actualExceptionCount.get(), "예상 예외 발생 횟수가 일치해야 합니다.");
    }

    // 포인트 사용 시 잔고가 음수가 되지 않도록 하는 테스트
    @Test
    void testNegativeBalancePrevention() throws InterruptedException, ExecutionException {
        long userId = 2L;
        pointService.charge(userId, 5000);
        int usageAmount = 1000;
        int threadCount = 10;

        System.out.println("🚀[테스트 시작] testNegativeBalancePrevention - 예상 최종 잔고: " + (5000 - usageAmount * threadCount));
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                try { pointService.use(userId, usageAmount); } catch (Exception ignored) {}
                return null;
            });
        }
        executor.invokeAll(tasks);
        executor.shutdown();

        UserPoint result = pointService.getUserPoint(userId);
        System.out.println("🚀[테스트 종료] testNegativeBalancePrevention - 최종 잔고: " + result.point());
        assertTrue(result.point() >= 0, "잔고가 음수가 되었습니다");
    }

    // 모든 충전 요청이 성공적으로 처리되는지 확인하는 테스트
    @Test
    void testAllChargesProcessedSuccessfully() throws InterruptedException, ExecutionException {
        long userId = 3L;
        int chargeAmount = 500;
        int threadCount = 10;
        AtomicInteger successCount = new AtomicInteger(0);
        System.out.println("🚀[테스트 시작] testAllChargesProcessedSuccessfully - 예상 최종 잔고: " + (chargeAmount * threadCount));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                try {
                    pointService.charge(userId, chargeAmount);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }
        executor.invokeAll(tasks);
        executor.shutdown();
        UserPoint result = pointService.getUserPoint(userId);
        System.out.println("🚀[테스트 종료] testAllChargesProcessedSuccessfully - 최종 잔고: " + result.point());

        assertEquals(threadCount, successCount.get(), "모든 충전 요청이 성공하지 않았습니다");
    }

    // 동시성 충전 테스트: 스레드가 동시에 충전 요청을 보내는 경우
    @Test
    void testNoStarvationInConcurrentCharges() throws InterruptedException {
        long userId = 5L;
        int chargeAmount = 1000;
        int threadCount = 10; // 실제 동시성 테스트를 위해 10으로 설정 (또는 더 큰 값으로 설정 가능).
        long expectedFinalPoint = (long)chargeAmount * threadCount;

        System.out.println("🚀[테스트 시작] testNoStarvationInConcurrentCharges - 예상 최종 잔고: " + expectedFinalPoint);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger unexpectedExceptionCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Void>> futures = new ArrayList<>(); // List<Future>를 직접 사용하도록 변경

        // 1. 모든 작업을 스레드 풀에 제출하고 Future 객체를 받습니다.
        // 이 시점에서 스레드들은 latch.await()에서 대기하기 시작합니다.
        for (int i = 0; i < threadCount; i++) {
            // 노션: 동시성 제어 방식에 대한 분석 및 보고서에서 확인 가능(https://www.notion.so/1fb3266f8e9180cc9a1dd8698458a5f1?pvs=4)
            futures.add(executor.submit(() -> { // invokeAll 대신 executor.submit() 사용,
                try {
                    latch.await(); // 모든 스레드가 준비될 때까지 대기
                    pointService.charge(userId, chargeAmount);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("### 경고: 스레드 인터럽트 발생: " + e.getMessage());
                    unexpectedExceptionCount.incrementAndGet();
                } catch (IllegalArgumentException e) {
                    System.err.println("### 오류: charge 메서드에서 예상치 못한 IllegalArgumentException 발생: " + e.getMessage());
                    unexpectedExceptionCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("### 치명적 오류: 예상치 못한 Exception 발생: " + e.getMessage());
                    unexpectedExceptionCount.incrementAndGet();
                }
                return null;
            }));
        }
        System.out.println("🚀[로그:정현진] 모든 작업 제출 완료. tasks.size() = " + futures.size());

        // 2. 모든 스레드를 동시에 시작!
        latch.countDown();
        System.out.println("🚀[로그:정현진] latch.countDown() 호출됨.");

        // 3. 모든 작업이 완료될 때까지 기다리고, 발생한 예외를 확인
        for (Future<Void> f : futures) {
            try {
                f.get();
            } catch (ExecutionException e) {
                System.err.println("### 오류: Future.get()에서 ExecutionException 발생: " + e.getCause().getMessage());
                unexpectedExceptionCount.incrementAndGet();
            }
        }
        executor.shutdown();
        System.out.println("🚀[로그:정현진] 모든 작업 완료 및 Executor shutdown.");

        // 최종 잔고 확인
        UserPoint result = pointService.getUserPoint(userId);
        System.out.println("🚀[테스트 종료] testNoStarvationInConcurrentCharges - 최종 잔고: " + result.point());

        assertEquals(expectedFinalPoint, result.point(), "모든 스레드가 성공적으로 충전되어야 합니다. 최종 잔고가 예상과 다릅니다.");
        assertEquals(0, unexpectedExceptionCount.get(), "테스트 중 예상치 못한 예외가 발생했습니다. 로그를 확인하세요.");
    }

    // 여러 사용자에 대해 동시성 충전 테스트
    @Test
    void testConcurrentChargesForMultipleUsers() throws InterruptedException {
        int chargeAmount = 100;
        int threadCount = 20; // 각 사용자에 대해 생성할 스레드 수
        int numberOfUsers = 5; // 테스트할 사용자 수

        int totalTasks = numberOfUsers * threadCount;

        // 스레드 풀은 모든 작업이 동시에 실행될 수 있도록 충분한 크기로 설정
        ExecutorService executor = Executors.newFixedThreadPool(totalTasks);

        List<Future<Void>> futures = new ArrayList<>(); // Callable 리스트 대신 Future 리스트로 변경

        // 모든 스레드가 준비될 때까지 기다렸다가 동시에 시작하기 위한 CountDownLatch
        CountDownLatch startLatch = new CountDownLatch(1);

        // 각 사용자별로 예상치 못한 예외 발생 횟수를 추적하기 위한 Map
        Map<Long, AtomicInteger> userUnexpectedExceptionCounts = new ConcurrentHashMap<>();

        System.out.println("\n--- 🚀 테스트 시작: testConcurrentChargesForMultipleUsers ---");
        System.out.println("  - 충전 금액: " + chargeAmount);
        System.out.println("  - 사용자당 스레드 수: " + threadCount);
        System.out.println("  - 테스트 사용자 수: " + numberOfUsers);
        System.out.println("  - 총 실행될 작업 수: " + totalTasks);

        // 1. 각 사용자에 대한 초기 포인트 설정 (필수!)
        // 및 예외 카운트 맵 초기화
        for (int i = 1; i <= numberOfUsers; i++) {
            // 객체 생성 주기 고려 사항(https://www.notion.so/1fb3266f8e91800281efef6c81897535?pvs=4)
            // 쓰레드간 충돌을 피하기 위해 각 사용자에 대해 별도의 UserPointTable 객체를 생성 함
            UserPointTable userPointTable = new UserPointTable();
            userPointTable.insertOrUpdate(i, 0L);
            // NullPointerException (Map.get() 반환값 null) 오류 분석 보고서(https://www.notion.so/NullPointerException-Map-get-null-1fb3266f8e918029afd5eb9d10fa4386?pvs=4)
            userUnexpectedExceptionCounts.put((long)i, new AtomicInteger(0)); // 각 userId에 대해 0으로 초기화된 AtomicInteger를 맵에 삽입합니다.
            System.out.println("🚀[로그] 사용자 ID: " + i + " 초기 잔고 0으로 설정 완료.");
        }

        // 2. 모든 Callable 작업을 스레드 풀에 비동기적으로 제출합니다.
        // 각 스레드는 startLatch.await()에서 대기합니다.
        for (int i = 1; i <= numberOfUsers; i++) {
            long userId = i;

            for (int j = 0; j < threadCount; j++) {
                futures.add(executor.submit(() -> { // <<< 다시 executor.submit() 사용!, invokeAll() 사용 시 교착 상태 발생
                    String threadName = Thread.currentThread().getName();
                    try {
                        System.out.println(threadName + " (User " + userId + ") - 시작 대기 중...");
                        startLatch.await(); // 모든 스레드가 준비될 때까지 대기

                        System.out.println(threadName + " (User " + userId + ") - 충전 시작: " + chargeAmount);
                        pointService.charge(userId, chargeAmount);
                        System.out.println(threadName + " (User " + userId + ") - 충전 완료.");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.err.println("### 경고: " + threadName + " (User " + userId + ") - 스레드 인터럽트 발생: " + e.getMessage());
                        userUnexpectedExceptionCounts.get(userId).incrementAndGet();
                    } catch (IllegalArgumentException e) {
                        System.err.println("### 오류: " + threadName + " (User " + userId + ") - IllegalArgumentException 발생: " + e.getMessage());
                        userUnexpectedExceptionCounts.get(userId).incrementAndGet();
                    } catch (Exception e) {
                        System.err.println("### 치명적 오류: " + threadName + " (User " + userId + ") - 예상치 못한 Exception 발생: " + e.getMessage());
                        userUnexpectedExceptionCounts.get(userId).incrementAndGet();
                    }
                    return null;
                }));
            }
        }
        System.out.println("🚀[로그] 총 " + totalTasks + "개의 충전 작업이 스레드 풀에 제출되었습니다.");

        // 3. 모든 스레드를 동시에 시작!
        startLatch.countDown(); // <<< 메인 스레드가 즉시 호출하여 대기 중인 스레드들을 해제
        System.out.println("🚀[로그] CountDownLatch.countDown() 호출됨. 모든 스레드 동시 시작!");

        // 4. 모든 작업이 완료될 때까지 기다리고, 발생한 예외를 확인
        for (Future<Void> f : futures) {
            try {
                f.get(); // 각 작업 완료 대기 및 ExecutionException 확인
            } catch (ExecutionException e) {
                System.err.println("### 오류: Future.get()에서 ExecutionException 발생: " + e.getCause().getMessage());
                // 이미 Callable 내부에서 카운트했으므로 여기서 또 카운트할 필요는 없지만,
                // fail()을 통해 테스트 실패를 명확히 하는 것이 좋습니다.
                fail("하나 이상의 작업에서 예상치 못한 예외 발생 (ExecutionException): " + e.getCause().getMessage(), e.getCause());
            }
        }
        executor.shutdown();
        System.out.println("🚀[로그] 모든 작업 완료 및 ExecutorService 종료.");
        System.out.println("🚀[로그] 사용자별 예상치 못한 예외 발생 횟수: " + userUnexpectedExceptionCounts);

        // 5. 각 사용자의 최종 잔고 확인 및 예외 카운트 검증
        for (int i = 1; i <= numberOfUsers; i++) {
            long userId = i;
            UserPoint result = pointService.getUserPoint(userId);
            long expectedPoint = (long)chargeAmount * threadCount;

            System.out.println("🚀[결과] 사용자 " + userId + " 최종 잔고: " + result.point() + ", 예상 잔고: " + expectedPoint);

            // 디버그를 위한 로그 추가
            System.out.println("DEBUG: Checking userId: " + userId + ", Map contains key: " + userUnexpectedExceptionCounts.containsKey(userId));

            /*
            System.out.println("🚀[결과] 사용자 " + userId + " 최종 잔고: " + result.point() + ", 예상 잔고: " + expectedPoint);

            assertEquals(expectedPoint, result.point(), "사용자 " + userId + "의 잔고가 잘못되었습니다.");
            * */
            // 문제가 발생한 라인
            int actualUnexpectedExceptionCount = userUnexpectedExceptionCounts.get(userId).get();
            assertEquals(0, actualUnexpectedExceptionCount,
                    "사용자 " + userId + "의 충전 작업 중 " + actualUnexpectedExceptionCount + "개의 예상치 못한 예외가 발생했습니다.");
        }
        System.out.println("--- ✅ 테스트 종료: testConcurrentChargesForMultipleUsers ---");
    }


    // 동시성 환경에서 실패하는 요청의 트랜잭션 롤백 검증
    @Test
    @DisplayName("동시성 환경에서 실패하는 요청의 트랜잭션 롤백 검증")
    void testConcurrentRollbackWithOtherSuccesses() throws InterruptedException {
        long userId = 50L; // 새로운 사용자 ID
        long initialBalance = 0; // 충분한 초기 잔고
        int numberOfSuccessCharges = 10; // 성공할 충전 요청 수
        int amountPerCharge = 1000;      // 각 성공 충전 금액
        long problematicAmount = -500;  // 실패를 유도할 음수 금액
        int totalTasks = numberOfSuccessCharges + 1; // 성공 요청들 + 1개의 실패 요청

        // 1. 초기 사용자 포인트 설정
        pointService.charge(userId, initialBalance);
        System.out.println("\n🚀[테스트 시작] testConcurrentRollbackWithOtherSuccesses - 사용자 " + userId + " 초기 잔고: " + initialBalance);

        UserPoint userAfterSetup = pointService.getUserPoint(userId);
        assertNotNull(userAfterSetup, "초기 사용자 설정 후 UserPoint가 null이면 안됩니다.");
        assertEquals(initialBalance, userAfterSetup.point(), "초기 잔고 설정이 잘못되었습니다.");

        ExecutorService executor = Executors.newFixedThreadPool(totalTasks);
        List<Future<Void>> futures = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger failedOperationCount = new AtomicInteger(0); // 실패 요청이 발생했는지 확인
        AtomicInteger successfulOperationCount = new AtomicInteger(0); // 성공 요청이 발생했는지 확인

        // 2. 성공적인 충전/사용 요청들 제출
        for (int i = 0; i < numberOfSuccessCharges; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    // pointService.charge() 또는 pointService.use() 사용 가능
                    // 여기서는 편의상 charge로 통일
                    pointService.charge(userId, amountPerCharge);
                    successfulOperationCount.incrementAndGet();
                    System.out.println(Thread.currentThread().getName() + " (User " + userId + ") - 성공 충전 완료.");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("### 경고: 성공 요청 스레드 인터럽트.");
                } catch (Exception e) {
                    System.err.println("### 오류: 성공 요청 중 예상치 못한 예외: " + e.getMessage());
                    fail("성공해야 할 요청이 실패했습니다: " + e.getMessage());
                }
                return null;
            }));
        }

        // 3. 실패를 유도하는 요청 제출 (롤백 대상)
        futures.add(executor.submit(() -> {
            try {
                startLatch.await();
                pointService.charge(userId, problematicAmount); // 실패 유도
                fail("실패해야 할 charge() 메소드가 예상된 예외를 던지지 않고 성공했습니다.");
            } catch (IllegalArgumentException e) {
                System.out.println(Thread.currentThread().getName() + " (User " + userId + ") - 예상된 IllegalArgumentException 발생 (롤백 예상).");
                failedOperationCount.incrementAndGet(); // 실패 요청 발생 카운트
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("### 경고: 실패 요청 스레드 인터럽트.");
            } catch (Exception e) {
                fail("실패 요청 중 예상치 못한 다른 예외 발생: " + e.getMessage(), e);
            }
            return null;
        }));

        System.out.println("🚀[로그] 모든 동시성 작업이 스레드 풀에 제출되었습니다.");

        // 4. 모든 스레드 동시 시작!
        startLatch.countDown();
        System.out.println("🚀[로그] CountDownLatch.countDown() 호출됨. 모든 스레드 동시 시작!");

        // 5. 모든 작업 완료 대기
        for (Future<Void> f : futures) {
            try {
                f.get();
            } catch (ExecutionException e) {
                fail("비동기 작업 실행 중 ExecutionException 발생: " + e.getCause().getMessage(), e.getCause());
            }
        }
        executor.shutdown();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            System.err.println("경고: ExecutorService가 지정된 시간 내에 종료되지 않았습니다.");
        }
        System.out.println("🚀[로그] 모든 동시성 작업 완료 및 ExecutorService 종료.");

        // 6. 최종 잔고 및 작업 결과 검증
        UserPoint finalUserPoint = pointService.getUserPoint(userId);
        assertNotNull(finalUserPoint, "최종 UserPoint 객체가 null이면 안됩니다.");

        // 예상 최종 잔고 = 초기 잔고 + (성공 충전 횟수 * 각 충전 금액)
        long expectedFinalBalance = initialBalance + (long)numberOfSuccessCharges * amountPerCharge;

        System.out.println("🚀[테스트 종료] testConcurrentRollbackWithOtherSuccesses - 사용자 " + userId + " 최종 잔고: " + finalUserPoint.point() + ", 예상 잔고: " + expectedFinalBalance);

        // 롤백될 요청은 잔고에 영향을 미치지 않아야 합니다.
        assertEquals(expectedFinalBalance, finalUserPoint.point(),
                "동시성 환경에서 실패 요청이 롤백된 후 최종 잔고가 예상과 다릅니다. 이는 트랜잭션 독립성 또는 롤백 실패를 의미할 수 있습니다.");

        // 실패 요청이 최소 한 번 발생했는지 확인
        assertEquals(1, failedOperationCount.get(), "실패를 유도하는 요청이 정확히 한 번 발생해야 합니다.");
        // 성공 요청들이 모두 실행되었는지 확인
        assertEquals(numberOfSuccessCharges, successfulOperationCount.get(), "모든 성공 요청이 정상적으로 처리되어야 합니다.");

        System.out.println("--- ✅ 테스트 종료: testConcurrentRollbackWithOtherSuccesses ---");
    }

}
