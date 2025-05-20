package io.hhplus.tdd;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointHistory;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * PointService의 동작을 테스트하기 위한 테스트 클래스
 */
class PointServiceStubTest {

    private UserPointTable userPointTable; // 사용자 포인트 테이블(Mock 객체)
    private PointHistoryTable pointHistoryTable; // 포인트 히스토리 테이블(Mock 객체)
    private PointService pointService; // 테스트 대상 서비스

    /**
     * 각 테스트 실행 전에 Mock 객체 초기화 및 PointService 인스턴스 생성
     */
    @BeforeEach
    void setUp() {
        userPointTable = mock(UserPointTable.class); // UserPointTable Mock 생성
        pointHistoryTable = mock(PointHistoryTable.class); // PointHistoryTable Mock 생성
        pointService = new PointService(userPointTable, pointHistoryTable); // PointService 초기화
    }

    /**
     * getUserPoint 메서드가 올바르게 사용자 포인트를 반환하는지 테스트
     */
    @Test
    void getUserPointShouldReturnUserPoint() {
        // Mock 설정: ID가 1인 사용자 포인트 반환
        when(userPointTable.selectById(1L)).thenReturn(new UserPoint(1L, 0L, System.currentTimeMillis()));

        // 메서드 호출 및 결과 검증
        UserPoint result = pointService.getUserPoint(1L);
        assertEquals(1L, result.id()); // ID가 1인지 확인
        verify(userPointTable).selectById(1L); // selectById 호출 여부 확인
    }

    /**
     * charge 메서드가 사용자 포인트를 올바르게 충전하는지 테스트
     */
    @Test
    void chargeShouldInsertAndUpdateUserPoint() {
        // Mock 설정: 초기 포인트와 충전 후 포인트 반환
        when(userPointTable.selectById(1L)).thenReturn(new UserPoint(1L, 0L, System.currentTimeMillis()));
        when(userPointTable.insertOrUpdate(1L, 1000L)).thenReturn(new UserPoint(1L, 1000L, System.currentTimeMillis()));

        // 메서드 호출 및 결과 검증
        UserPoint charged = pointService.charge(1L, 1000L);
        assertEquals(1000L, charged.point()); // 충전 후 포인트 확인
        verify(userPointTable).insertOrUpdate(1L, 1000L); // insertOrUpdate 호출 여부 확인
    }

    /**
     * use 메서드가 사용자 포인트를 올바르게 차감하는지 테스트
     */
    @Test
    void useShouldInsertAndUpdateUserPoint() {
        // Mock 설정: 초기 포인트와 차감 후 포인트 반환
        when(userPointTable.selectById(1L)).thenReturn(new UserPoint(1L, 2000L, System.currentTimeMillis()));
        when(userPointTable.insertOrUpdate(1L, 1500L)).thenReturn(new UserPoint(1L, 1500L, System.currentTimeMillis()));

        // 메서드 호출 및 검증
        pointService.use(1L, 500L);
        verify(userPointTable).insertOrUpdate(1L, 1500L); // insertOrUpdate 호출 여부 확인
    }

    /**
     * updateHistory 메서드가 포인트 히스토리를 올바르게 업데이트하는지 테스트
     */
    @Test
    void updateHistoryShouldInsertToPointHistoryTable() {
        // 메서드 호출
        pointService.updateHistory(1L, 1000L, TransactionType.CHARGE);

        // insert 호출 여부 확인
        verify(pointHistoryTable).insert(1L, 1000L, TransactionType.CHARGE, System.currentTimeMillis());
    }

    /**
     * 잔액 부족 시 use 메서드가 예외를 던지는지 테스트
     */
    @Test
    void insufficientBalanceShouldThrow() {
        // Mock 설정: 잔액이 부족한 사용자 포인트 반환
        when(userPointTable.selectById(1L)).thenReturn(new UserPoint(1L, 500L, System.currentTimeMillis()));

        // 예외 발생 여부 확인
        assertThrows(IllegalArgumentException.class, () -> pointService.use(1L, 1000L));
    }

    /**
     * 최대 잔액 초과 시 charge 메서드가 예외를 던지는지 테스트
     */
    @Test
    void overMaxBalanceShouldThrow() {
        // Mock 설정: 최대 잔액에 가까운 사용자 포인트 반환
        when(userPointTable.selectById(1L)).thenReturn(new UserPoint(1L, 9500L, System.currentTimeMillis()));

        // 예외 발생 여부 확인
        assertThrows(IllegalArgumentException.class, () -> pointService.charge(1L, 1000L));
    }

    /**
     * 음수 금액 충전 시 charge 메서드가 예외를 던지는지 테스트
     */
    @Test
    void negativeChargeShouldThrow() {
        // 예외 발생 여부 확인
        assertThrows(IllegalArgumentException.class, () -> pointService.charge(1L, -100L));
    }

    /**
     * 잔액이 0이 되는 경우를 허용하는지 테스트
     */
    @Test
    void zeroBalanceAllowed() {
        // Mock 설정: 초기 포인트와 차감 후 포인트 반환
        when(userPointTable.selectById(1L)).thenReturn(new UserPoint(1L, 1000L, System.currentTimeMillis()));
        when(userPointTable.insertOrUpdate(1L, 0L)).thenReturn(new UserPoint(1L, 0L, System.currentTimeMillis()));

        // 메서드 호출 및 검증
        pointService.use(1L, 1000L);
        verify(userPointTable).insertOrUpdate(1L, 0L); // insertOrUpdate 호출 여부 확인
    }

    /**
     * getPointHistory 메서드가 사용자 포인트 히스토리를 올바르게 반환하는지 테스트
     */
    @Test
    void getPointHistoryShouldReturnList() {
        // Mock 설정: 포인트 히스토리 반환
        List<PointHistory> histories = List.of(new PointHistory(1L, 1L, 1000L, TransactionType.CHARGE, System.currentTimeMillis()));
        when(pointHistoryTable.selectAllByUserId(1L)).thenReturn(histories);

        // 메서드 호출 및 결과 검증
        List<PointHistory> result = pointService.getPointHistory(1L);
        assertEquals(1, result.size()); // 히스토리 개수 확인
        assertEquals(1000L, result.get(0).amount()); // 첫 번째 히스토리 금액 확인
        verify(pointHistoryTable).selectAllByUserId(1L); // selectAllByUserId 호출 여부 확인
    }
}