package io.stargate.sgv3.docsapi.service.sequencer;

import io.smallrye.mutiny.Uni;
import io.stargate.sgv3.docsapi.service.bridge.executor.ReactiveQueryExecutor;

/**
 * Marker for reactive execution.
 *
 * @param <RESULT> Result of the execution.
 */
public interface ReactiveQuerySequenceExecutor<RESULT> {

  /**
   * Executes given the {@link ReactiveQueryExecutor}.
   *
   * @param queryExecutor {@link ReactiveQueryExecutor}
   * @return Result
   */
  // use reactive executor here
  Uni<RESULT> execute(ReactiveQueryExecutor queryExecutor);
}
