package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import org.springframework.stereotype.Service;

@Service("pointService")
public class PointService {

    private final UserPointTable userPointTable;

    public PointService(UserPointTable userPointTable) {
        this.userPointTable = userPointTable;
    }

    public UserPoint getUserPoint(long id) {
        return userPointTable.selectById(id);
    }

    public UserPoint charge(long id, long amount) {
        return userPointTable.insertOrUpdate(id, amount);
    }

    public UserPoint use(long id, long amount) {
        return userPointTable.insertOrUpdate(id, -amount);
    }

}