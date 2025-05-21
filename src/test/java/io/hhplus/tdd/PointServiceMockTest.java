//package io.hhplus.tdd;
//
//import io.hhplus.tdd.database.PointHistoryTable;
//import io.hhplus.tdd.database.UserPointTable;
//import io.hhplus.tdd.point.PointService;
//import io.hhplus.tdd.point.UserPoint;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.*;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertThrows;
//
//class PointServiceMockTest {
//
//    //- 포인트 충전, 사용에 대한 정책 추가 (잔고 부족, 최대 잔고 등)
//    //- 동시에 여러 요청이 들어오더라도 순서대로 (혹은 한번에 하나의 요청씩만) 제어될 수 있도록 리팩토링
//    //- 동시성 제어에 대한 통합 테스트 작성
//
//    private PointService pointService;
//
//    @BeforeEach
//    void setUp() {
//        // 각 테스트 실행 전에 PointService를 초기화합니다.
//        // UserPointTable과 PointHistoryTable은 PointService의 의존성으로 사용됩니다.
//        UserPointTable userPointTable = new UserPointTable();
//        PointHistoryTable pointHistoryTable = new PointHistoryTable();
//        pointService = new PointService(userPointTable, pointHistoryTable);
//    }
//
//    @Test
//    void testChargeWithinLimit() {
//        // 사용자가 포인트를 충전할 때, 최대 잔고를 초과하지 않는 경우를 테스트합니다.
//        long userId = 1L;
//        UserPoint result = pointService.charge(userId, 5000);
//        assertEquals(5000, result.point()); // 충전된 포인트가 예상 값과 일치하는지 확인합니다.
//    }
//
//    @Test
//    void testChargeExceedingMaxBalance() {
//        // 사용자가 포인트를 충전할 때, 최대 잔고를 초과하는 경우를 테스트합니다.
//        long userId = 1L;
//        pointService.charge(userId, 9000); // 초기 충전
//        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
//            pointService.charge(userId, 2000); // 최대 잔고 초과 충전 시도
//        });
//        assertEquals("최대 잔고는 10,000원을 초과할 수 없습니다.", exception.getMessage()); // 예외 메시지가 예상과 일치하는지 확인합니다.
//    }
//
//    @Test
//    void testUseWithInsufficientBalance() {
//        // 사용자가 포인트를 사용할 때, 잔고가 부족한 경우를 테스트합니다.
//        long userId = 1L;
//        pointService.charge(userId, 1000); // 초기 충전
//        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
//            pointService.use(userId, 2000); // 잔고 초과 사용 시도
//        });
//        assertEquals("잔고가 부족합니다.", exception.getMessage()); // 예외 메시지가 예상과 일치하는지 확인합니다.
//    }
//
//    @Test
//    void testConcurrentChargesAreSynchronized() throws InterruptedException, ExecutionException {
//        // 여러 스레드가 동시에 포인트를 충전할 때, 동기화가 제대로 이루어지는지 테스트합니다.
//        long userId = 2L;
//        ExecutorService executor = Executors.newFixedThreadPool(10); // 10개의 스레드 풀 생성
//        List<Callable<Void>> tasks = new ArrayList<>();
//
//        for (int i = 0; i < 10; i++) {
//            tasks.add(() -> {
//                pointService.charge(userId, 1000); // 각 스레드가 1000 포인트 충전
//                return null;
//            });
//        }
//
//        List<Future<Void>> futures = executor.invokeAll(tasks); // 모든 태스크 실행
//        for (Future<Void> f : futures) {
//            f.get(); // 각 태스크의 실행 결과를 기다림
//        }
//        executor.shutdown(); // 스레드 풀 종료
//
//        UserPoint result = pointService.getUserPoint(userId);
//        assertEquals(10000, result.point()); // 최종 잔고가 예상 값(10,000)과 일치하는지 확인합니다.
//    }
//
//    @Test
//    void testConcurrentUseIsSynchronized() throws InterruptedException, ExecutionException {
//        // 여러 스레드가 동시에 포인트를 사용할 때, 동기화가 제대로 이루어지는지 테스트합니다.
//        long userId = 3L;
//        pointService.charge(userId, 10000); // 초기 충전
//
//        ExecutorService executor = Executors.newFixedThreadPool(10); // 10개의 스레드 풀 생성
//        List<Callable<Void>> tasks = new ArrayList<>();
//
//        for (int i = 0; i < 10; i++) {
//            tasks.add(() -> {
//                pointService.use(userId, 1000); // 각 스레드가 1000 포인트 사용
//                return null;
//            });
//        }
//
//        List<Future<Void>> futures = executor.invokeAll(tasks); // 모든 태스크 실행
//        for (Future<Void> f : futures) {
//            f.get(); // 각 태스크의 실행 결과를 기다림
//        }
//        executor.shutdown(); // 스레드 풀 종료
//
//        UserPoint result = pointService.getUserPoint(userId);
//        assertEquals(0, result.point()); // 최종 잔고가 예상 값(0)과 일치하는지 확인합니다.
//    }
//}