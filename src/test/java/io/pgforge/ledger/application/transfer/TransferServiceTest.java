package io.pgforge.ledger.application.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.pgforge.ledger.domain.account.AccountId;
import io.pgforge.ledger.domain.account.Money;
import io.pgforge.ledger.domain.transfer.TransferCommand;
import io.pgforge.ledger.domain.transfer.TransferOutcome;
import io.pgforge.ledger.domain.transfer.TransferStrategy;
import io.pgforge.ledger.domain.transfer.TransferStrategyName;

class TransferServiceTest {

    private final TransferCommand cmd =
            new TransferCommand(AccountId.of(1), AccountId.of(2), Money.of("10.00"));

    @Test
    void transfer_dispatchesToCorrectStrategy_byName() {
        FakeStrategy naive = new FakeStrategy(TransferStrategyName.NAIVE);
        FakeStrategy pessimistic = new FakeStrategy(TransferStrategyName.PESSIMISTIC);
        FakeStrategy optimistic = new FakeStrategy(TransferStrategyName.OPTIMISTIC);
        TransferService svc = new TransferService(Map.of(
                TransferStrategyName.NAIVE, naive,
                TransferStrategyName.PESSIMISTIC, pessimistic,
                TransferStrategyName.OPTIMISTIC, optimistic));

        svc.transfer(TransferStrategyName.PESSIMISTIC, cmd);
        assertThat(pessimistic.calls).isEqualTo(1);
        assertThat(naive.calls).isZero();
        assertThat(optimistic.calls).isZero();
    }

    @Test
    void constructor_rejectsIncompleteStrategyMap() {
        Map<TransferStrategyName, TransferStrategy> partial = new EnumMap<>(TransferStrategyName.class);
        partial.put(TransferStrategyName.NAIVE, new FakeStrategy(TransferStrategyName.NAIVE));
        assertThatThrownBy(() -> new TransferService(partial)).isInstanceOf(IllegalArgumentException.class);
    }

    private static final class FakeStrategy implements TransferStrategy {

        private final TransferStrategyName name;
        int calls;

        FakeStrategy(TransferStrategyName name) {
            this.name = name;
        }

        @Override
        public TransferOutcome execute(TransferCommand command) {
            calls++;
            return new TransferOutcome(1L, command.from(), command.to(), command.amount(), 1);
        }

        @Override
        public TransferStrategyName name() {
            return name;
        }
    }
}
