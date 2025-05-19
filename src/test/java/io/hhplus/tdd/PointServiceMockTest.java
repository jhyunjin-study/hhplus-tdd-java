package io.hhplus.tdd;

import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PointServiceMockTest {

    @Test
    void testGetUserPoint() {
        System.out.println("🚀[로그:정현진]=========================");
        System.out.println("🚀[로그:정현진]testGetUserPoint");
        // Arrange
        PointService pointService = mock(PointService.class);
        UserPoint mockUserPoint = new UserPoint(1L, 100L, System.currentTimeMillis());
        when(pointService.getUserPoint(1L)).thenReturn(mockUserPoint);

        // Act
        UserPoint result = pointService.getUserPoint(1L);

        // Assert
        assertEquals(1L, result.id());
        assertEquals(100L, result.point());
        System.out.println("🚀[로그:정현진]UserPoint ID: " + result.id());
        System.out.println("🚀[로그:정현진]UserPoint Amount: " + result.point());
    }

    @Test
    void testGetUserPointWithAnyId() {
        System.out.println("🚀[로그:정현진]=========================");
        System.out.println("🚀[로그:정현진]testGetUserPointWithAnyId");
        // Arrange
        PointService pointService = mock(PointService.class);
        when(pointService.getUserPoint(anyLong()))
                .thenReturn(new UserPoint(999L, 200L, System.currentTimeMillis()));

        // Act
        UserPoint result = pointService.getUserPoint(123L);

        // Assert
        assertEquals(999L, result.id());
        assertEquals(200L, result.point());
        System.out.println("🚀[로그:정현진]UserPoint ID: " + result.id());
        System.out.println("🚀[로그:정현진]UserPoint Amount: " + result.point());
    }

    @Test
    void testCharge() {
        System.out.println("🚀[로그:정현진]=========================");
        System.out.println("🚀[로그:정현진]testCharge");
        //Arrange
        PointService pointService = mock(PointService.class);
        UserPoint mockUserPoint = new UserPoint(1L, 100L, System.currentTimeMillis());
        when(pointService.charge(1L, 100L)).thenReturn(mockUserPoint);
        //Act
        UserPoint result = pointService.charge(1L, 100L);
        //Assert
        assertEquals(1L, result.id());
        assertEquals(100L, result.point());
        System.out.println("🚀[로그:정현진]UserPoint ID: " + result.id());
        System.out.println("🚀[로그:정현진]UserPoint Amount: " + result.point());
    }

    @Test
    void testUse(){
        System.out.println("🚀[로그:정현진]=========================");
        System.out.println("🚀[로그:정현진]testUse");
        //Arrange
        PointService pointService = mock(PointService.class);
        UserPoint mockUserPoint = new UserPoint(1L, 100L, System.currentTimeMillis());
        when(pointService.use(1L, 50L)).thenReturn(mockUserPoint);
        //Act
        UserPoint result = pointService.use(1L, 50L);
        //Assert
        assertEquals(1L, result.id());
        assertEquals(100L, result.point());
        System.out.println("🚀[로그:정현진]UserPoint ID: " + result.id());
        System.out.println("🚀[로그:정현진]UserPoint Amount: " + result.point());
    }




}
