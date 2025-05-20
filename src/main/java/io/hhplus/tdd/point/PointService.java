package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service("pointService")
public class PointService {

    // 사용자 포인트 정보를 저장하는 테이블
    private final UserPointTable userPointTable;

    // 포인트 내역 정보를 저장하는 테이블
    private final PointHistoryTable pointHistoryTable;

    // 생성자: 의존성 주입을 통해 UserPointTable과 PointHistoryTable을 초기화
    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    // 특정 사용자의 포인트 정보를 조회
    public UserPoint getUserPoint(long id) {
        return userPointTable.selectById(id);
    }

    // 특정 사용자의 포인트를 충전
    public UserPoint charge(long userId, long amount) {
        // 충전 금액이 0보다 작으면 예외 발생
        if (amount < 0) {
            throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다.");
        }

        // 사용자별로 동기화 처리하여 충돌 방지
        synchronized (getLock(userId)) {
            // 사용자 포인트 정보 조회
            UserPoint up = userPointTable.selectById(userId);
            if (up == null) {
                throw new IllegalArgumentException("존재하지 않는 사용자입니다.");
            }

            // 새로운 포인트 계산
            long newPoint = up.point() + amount;
            // 최대 잔고 제한 초과 시 예외 발생
            if (newPoint > 10000) {
                throw new IllegalArgumentException("최대 잔고는 10,000원을 초과할 수 없습니다.");
            }

            // 포인트 정보를 업데이트
            return userPointTable.insertOrUpdate(userId, newPoint);
        }
    }

    // 특정 사용자의 포인트를 사용
    public void use(long userId, long amount) {
        // 사용자별로 동기화 처리하여 충돌 방지
        synchronized (getLock(userId)) {
            // 사용자 포인트 정보 조회
            UserPoint up = userPointTable.selectById(userId);
            // 잔고가 부족하면 예외 발생
            if (up.point() < amount) throw new IllegalArgumentException("잔고가 부족합니다.");
            // 포인트 정보를 업데이트
            userPointTable.insertOrUpdate(userId, up.point() - amount);
        }
    }

    // 사용자별 동기화를 위한 락 객체를 저장하는 맵
    private final ConcurrentHashMap<Long, Object> userLocks = new ConcurrentHashMap<>();

    // 특정 사용자에 대한 락 객체를 반환 (없으면 새로 생성)
    private Object getLock(long userId) {
        return userLocks.computeIfAbsent(userId, id -> new Object());
    }

    // 포인트 내역을 업데이트
    public PointHistory updateHistory(long userId, long amount, TransactionType type) {
        // 포인트 내역을 테이블에 삽입
        return pointHistoryTable.insert(userId, amount, type, System.currentTimeMillis());
    }

    // 특정 사용자의 포인트 내역을 조회
    public List<PointHistory> getPointHistory(long userId) {
        // 포인트 내역을 테이블에서 조회
        return pointHistoryTable.selectAllByUserId(userId);
    }
}