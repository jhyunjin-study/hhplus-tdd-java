📈 포인트 서비스 동시성 및 트랜잭션 제어 분석
이 문서는 TDD(Test-Driven Development) 방식으로 개발된 포인트 서비스의 동시성(Concurrency) 및 트랜잭션(Transaction) 제어 방식에 대한 상세 분석과 관련 통합 테스트 코드에 대해 설명합니다.

1. 개요 및 목표
   개발 중인 포인트 서비스는 사용자들의 포인트 충전 및 사용 요청을 처리합니다. 특히 여러 사용자가 동시에 포인트 관련 작업을 수행하거나, 단일 사용자에 대해 여러 요청이 동시에 발생할 수 있는 동시성 환경에서의 정확성과 신뢰성 보장이 중요합니다.

주요 목표는 다음과 같습니다:

정확한 포인트 계산: 동시성 환경에서도 최종 포인트 잔고가 항상 예상 값과 일치해야 합니다.
트랜잭션의 원자성(Atomicity): 포인트 충전/사용 작업은 트랜잭션으로 묶여야 하며, 중간에 오류가 발생하면 모든 변경 사항이 롤백되어야 합니다.
트랜잭션의 격리성(Isolation): 한 트랜잭션의 실패 및 롤백이 동시에 진행되는 다른 트랜잭션의 성공에 영향을 주지 않아야 합니다.
2. 동시성 제어 핵심 전략
   포인트 서비스의 동시성 및 트랜잭션 제어를 위해 다음과 같은 전략과 기술이 적용되었습니다.

2.1. UserPointTable의 원자적 업데이트 (AtomicInteger / 인메모리 DB)
포인트 서비스는 UserPointTable이라는 인메모리 데이터베이스를 사용하여 사용자별 포인트를 관리합니다. 동시성 환경에서 여러 스레드가 동시에 특정 사용자의 포인트를 업데이트할 때 데이터 일관성을 보장하기 위해 다음과 같은 방법을 사용합니다.

ConcurrentHashMap: 여러 스레드가 동시에 접근하여 읽고 쓸 수 있는 스레드 안전한 Map 구현체입니다. 사용자 ID(Long)를 키로 사용합니다.
AtomicInteger: 각 사용자 포인트 잔고를 저장하는 데 AtomicInteger (또는 AtomicLong)를 사용합니다. Atomic 접두사가 붙은 클래스들은 내부적으로 CAS(Compare-And-Swap) 연산과 같은 하드웨어 지원 원시 연산을 활용하여 락(Lock) 없이도 스레드 안전한 연산(예: incrementAndGet(), addAndGet())을 수행할 수 있도록 합니다. 이를 **비관적 락(Pessimistic Lock)**이나 **낙관적 락(Optimistic Lock)**과 같은 복잡한 동기화 메커니즘 없이도 원자성을 보장하는 록 프리(Lock-Free) 방식으로 볼 수 있습니다.

      Java

      // UserPointTable 내부 (예시)
      public class UserPointTable {
      private final Map<Long, UserPoint> table = new ConcurrentHashMap<>();
      
          public UserPoint insertOrUpdate(long userId, long amount) {
              // compute 메서드를 사용하여 해당 userId에 대한 업데이트를 원자적으로 수행
              return table.compute(userId, (key, existingUserPoint) -> {
                  long newPoint = (existingUserPoint != null) ? existingUserPoint.point() + amount : amount;
                  // 새로운 UserPoint 인스턴스를 생성하여 반환 (불변성 유지)
                  return new UserPoint(userId, newPoint, System.currentTimeMillis());
              });
          }
      }
      // UserPoint 클래스 (예시)
      PointService의 트랜잭션 관리:
      PointService는 UserPointTable의 저수준 동시성 제어와는 별개로, 여러 데이터 조작(UserPointTable 업데이트, PointHistoryTable 기록)에 걸쳐 원자성을 보장하는 트랜잭션 로직을 포함합니다. 이는 실제 운영 환경의 데이터베이스 트랜잭션과 유사하게, 중간에 예외 발생 시 모든 변경 사항이 롤백되도록 구현됩니다.

2.2. ExecutorService와 CountDownLatch를 활용한 동시성 테스트
통합 테스트에서는 실제 동시성 환경을 모방하여 서비스의 견고성을 검증합니다.

ExecutorService: 여러 스레드를 생성하고 관리하며, 비동기 작업을 병렬로 실행하는 데 사용됩니다. Executors.newFixedThreadPool을 통해 고정된 수의 스레드 풀을 생성하여 스레드 리소스를 효율적으로 관리합니다.
CountDownLatch: 모든 스레드가 특정 시점까지 대기했다가 동시에 작업을 시작하도록 하는 동기화 도구입니다. 이를 통해 실제 동시성 상황(Race Condition)을 정확하게 재현하고 테스트할 수 있습니다.
모든 작업(Callable)이 startLatch.await()를 호출하며 시작을 대기합니다.
메인 스레드에서 startLatch.countDown()을 호출하면, 대기 중이던 모든 스레드가 동시에 실행을 시작합니다.
3. 통합 테스트 케이스 분석
   두 가지 핵심 통합 테스트를 통해 동시성 및 트랜잭션 제어의 유효성을 검증합니다.

3.1. testConcurrentChargesForMultipleUsers (다중 사용자 동시 충전 테스트)
목표: 여러 사용자가 동시에 충전 요청을 보낼 때, 각 사용자의 최종 잔고가 정확하게 계산되는지 확인합니다.

시나리오:

여러 명의 사용자(numberOfUsers)를 설정하고 각 사용자의 초기 잔고를 0으로 설정합니다.
각 사용자에게 여러 개의 충전 요청(threadCount)을 동시에 보냅니다.
모든 요청이 완료된 후, 각 사용자의 최종 잔고가 (초기 잔고) + (충전 금액 * 요청 수)와 일치하는지 검증합니다.
이 과정에서 예상치 못한 예외(예: IllegalArgumentException 등)가 발생하지 않았는지 userUnexpectedExceptionCounts 맵을 통해 추적하고 검증합니다.
핵심 검증:

assertEquals(expectedPoint, result.point(), "사용자 N의 잔고가 잘못되었습니다.");
assertEquals(0, actualUnexpectedExceptionCount, "사용자 N의 충전 작업 중 예상치 못한 예외가 발생했습니다.");
결과: 이 테스트는 시스템이 다중 사용자 환경에서 동시에 발생하는 충전 요청들을 정확하고 안전하게 처리하여, 최종 잔고의 일관성을 유지하는지 검증합니다.

3.2. testConcurrentRollbackWithOtherSuccesses (동시성 환경 롤백 검증 테스트)
목표: 동시성 환경에서 특정 요청이 실패하여 트랜잭션 롤백이 발생하더라도, 동시에 실행된 다른 성공적인 요청들이 올바르게 처리되고 최종 잔고에 영향을 미치지 않는지 확인합니다. 이는 트랜잭션의 격리성과 원자성을 검증하는 데 중요합니다.

시나리오:

특정 사용자(userId)에게 0으로 초기 잔고를 설정합니다.
여러 개의 성공적인 충전 요청과 하나의 실패를 유도하는 요청 (예: 음수 금액 충전)을 동시에 보냅니다.
실패를 유도하는 요청은 IllegalArgumentException과 같은 예외를 발생시켜 PointService 내의 트랜잭션 롤백을 유도합니다.
모든 요청이 완료된 후, 최종 잔고가 (초기 잔고) + (성공적인 충전 요청 수 * 각 충전 금액)과 정확히 일치하는지 검증합니다. 실패로 인해 롤백된 요청은 최종 잔고에 반영되지 않아야 합니다.
AtomicInteger를 사용하여 실패 요청과 성공 요청이 각각 예상 횟수만큼 발생했는지 카운트하고 검증합니다.

핵심 검증: fail 

"charge() 메소드가 유효하지 않은 금액에 대해 예상된 예외를 던지지 않고 성공했습니다."); 
(실패 요청이 실제로 예외를 던지는지 확인)

assertEquals(expectedFinalBalance, finalUserPoint.point(), "동시성 환경에서 실패 요청이 롤백된 후 최종 잔고가 예상과 다릅니다.");  
(롤백이 다른 성공 요청에 영향을 미치지 않는지 확인)

assertEquals(1, failedOperationCount.get(), "실패를 유도하는 요청이 정확히 한 번 발생해야 합니다.");   

assertEquals(numberOfSuccessCharges, successfulOperationCount.get(), "모든 성공 요청이 정상적으로 처리되어야 합니다.");   

결과: 이 테스트는 시스템이 복잡한 동시성 시나리오에서도 트랜잭션의 **원자성(Atomicity)**과 **격리성(Isolation)**을 효과적으로 보장하여, 실패한 작업이 전체 시스템의 일관성을 해치지 않음을 입증합니다. 로그를 통해 성공적인 충전과 롤백되는 실패가 동시에 발생하고 최종 잔고가 정확히 계산되는 것을 확인했습니다.

4. 결론
   제시된 동시성 제어 전략과 통합 테스트는 포인트 서비스가 다중 사용자 및 동시성 환경에서 포인트 잔고의 일관성, 트랜잭션의 원자성 및 격리성을 효과적으로 보장하고 있음을 보여줍니다. 특히, ConcurrentHashMap과 같은 스레드 안전한 자료구조와 ExecutorService, CountDownLatch를 활용한 테스트 전략은 복잡한 동시성 문제를 검증하는 데 매우 유용합니다.