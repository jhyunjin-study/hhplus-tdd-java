package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/point")
public class PointController {

    private static final Logger log = LoggerFactory.getLogger(PointController.class);

    /**
     * TODO - 특정 유저의 포인트를 조회하는 기능을 작성해주세요.
     */
    private final PointService pointService;

    public PointController() {
        UserPointTable userPointTable = new UserPointTable();
        PointHistoryTable pointHistoryTable = new PointHistoryTable();
        this.pointService = new PointService(userPointTable, pointHistoryTable);
    }

    @GetMapping("{id}")
    public UserPoint point(@PathVariable long id) {
        log.info("point id: {}", id);
        UserPoint userPoint = pointService.getUserPoint(id);
        log.info("point userPoint: {}", userPoint);
        return userPoint;
    }

    /**
     * TODO - 특정 유저의 포인트 충전/이용 내역을 조회하는 기능을 작성해주세요.
     */
    @GetMapping("{id}/histories")
    public List<PointHistory> history(@PathVariable long id) {
        log.info("history id: {}", id);
        List<PointHistory> pointHistories = pointService.getPointHistory(id);
        log.info("history pointHistories: {}", pointHistories);
        return pointHistories;
    }

    /**
     * TODO - 특정 유저의 포인트를 충전하는 기능을 작성해주세요.
     */
    @PatchMapping("{id}/charge")
    public UserPoint charge(@PathVariable long id, @RequestBody long amount) {
        log.info("charge id: {}, amount: {}", id, amount);

        validateAmount(amount);
        validateUserId(id);

        UserPoint userPoint = pointService.charge(id, amount);
        log.info("charge userPoint: {}", userPoint);
        return userPoint;
    }

    /**
     * TODO - 특정 유저의 포인트를 사용하는 기능을 작성해주세요.
     */
    @PatchMapping("{id}/use")
    public UserPoint use(@PathVariable long id, @RequestBody long amount) {
        log.info("use id: {}, amount: {}", id, amount);

        pointService.use(id, amount);
        pointService.updateHistory(id, amount, TransactionType.USE);
        UserPoint userPoint = pointService.getUserPoint(id);
        log.info("use userPoint: {}", userPoint);
        return userPoint;
    }

    private void validateAmount(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다.");
        }
        if (amount > 10000) {
            throw new IllegalArgumentException("충전 금액은 10,000원 이하로 가능합니다.");
        }
        if (amount % 100 != 0) {
            throw new IllegalArgumentException("충전 금액은 100원 단위로 가능합니다.");
        }
    }

    private void validateUserId(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("유저 ID는 0보다 커야 합니다.");
        }
    }
}
