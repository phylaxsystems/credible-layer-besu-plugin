# BesuPlugin Mechanism and TransactionSelectionService

The BesuPlugin mechanism allows plugins to extend the functionality of the Besu node. The Credible Layer Besu Plugin implements the [TransactionSelectionService](https://github.com/hyperledger/besu/blob/main/plugin-api/src/main/java/org/hyperledger/besu/plugin/services/TransactionSelectionService.java) interface, which provides methods for transaction selection.

## TransactionSelectionService

The [TransactionSelectionService](https://github.com/hyperledger/besu/blob/main/plugin-api/src/main/java/org/hyperledger/besu/plugin/services/TransactionSelectionService.java) interface is defined in the `org.hyperledger.besu.plugin.services.TransactionSelectionService` class. 

The `TransactionSelectionService` provides the `registerPluginTransactionSelectorFactory` method which serves as a factory for [PluginTransactionSelector](https://github.com/hyperledger/besu/blob/main/plugin-api/src/main/java/org/hyperledger/besu/plugin/services/txselection/PluginTransactionSelector.java#L26) that have the following methods:

- `evaluateTransactionPreProcessing` - called before processing the transaction
- `evaluateTransactionPostProcessing` - called after the processing the transaction
- `onTransactionSelected` - called when a transaction is selected to be added to a block
- `onTransactionNotSelected` - called when a transaction is not selected to be added to a block

The `evaluateTransaction(Pre|Post)Processing` can also indicate that no further transactions can be added to the block.

## BesuPlugin Mechanism

The BesuPlugin mechanism is used by the Credible Layer Besu Plugin to interact with the Besu node. It provides a way for plugins to extend the functionality of the node.

Plugins are loaded by the Besu node at startup and registered in the plugin manager. The plugin manager provides methods for interacting with plugins, such as registering services and handling events.

For more information on the BesuPlugin mechanism, refer to the [Besu Plugin API documentation](https://besu.hyperledger.org/public-networks/reference/plugin-api-interfaces).


## Terminology

- `Transaction selection` - the process inside of the block builder to include or exclude a transaction from the block that's currently built
- `Commit Head` - the event of a block being added to the canonical chain
- `Iteration` - in one block building process, Besu tries to build a block multiple times (if there's enough time for it); the repetitions of creating a block multiple times are called `iterations`
- `Operation tracer` - a component of the Besu block builder that enables services to hook into specific points in the block/transaction processing/execution (block production started, transaction started, etc.)

## Communication with the Credible Layer

The Credible Layer Besu Plugin communicates with the Credible Layer using GRPC. It sends transaction data to the Credible Layer for evaluation and receives the evaluation results back.

## ISidecarTransport

The ISidecarTransport interface is defined in the `net.phylax.credible.transport.ISidecarTransport` class. It provides methods for communication with the Credible Layer sidecar, which is a separate process that runs alongside the Besu node.

The ISidecarTransport interface includes the following methods:

- `sendTransactions(SendTransactionsRequest transactions)` - Sends transaction data to the Credible Layer sidecar for evaluation and returns a `CompletableFuture<SendTransactionsResponse>`. Transaction completion is expected to arrive on `subscribeResults`.
- `getTransactions(GetTransactionsRequest transactions)` - Retrieves multiple transaction data from the Credible Layer sidecar based on the provided transaction request and returns a `CompletableFuture<GetTransactionsResponse>`.
- `getTransaction(GetTransactionRequest transactions)` - Retrieves a single transaction result from the Credible Layer sidecar based on the provided transaction request and returns a `CompletableFuture<GetTransactionResponse>`. This is a unary recovery lookup and may legally return `not_found` while processing is still in flight.
- `sendReorg(ReorgRequest reorgRequest)` - Sends a reorganization request to the Credible Layer sidecar and returns a `CompletableFuture<ReorgResponse>`.
- `sendEvents(SendEventsRequest events)` - Sends batched events to the Credible Layer sidecar and returns a `CompletableFuture<SendEventsResponse>`.
- `sendEvent(SendEventsRequestItem event)` - Sends a single event to the Credible Layer sidecar and returns a `CompletableFuture<Boolean>` acknowledging whether the event was accepted.
- `subscribeResults(Consumer<TransactionResult> onResult, Consumer<Throwable> onError)` - Opens the server-streaming results subscription. `SubscribeResults` is the primary path for transaction completion.
- `closeResultsSubscription()` - Closes the results subscription stream if one is open.

## ISidecarStrategy

The `ISidecarStrategy` interface is defined in the `net.phylax.credible.strategy.ISidecarStrategy` class. It abstracts the orchestration of calls to the transport by providing methods that the TransactionSelectionPlugin uses.

The `ISidecarStrategy` interface includes the following methods:

- `commitHead(CommitHead commitHead, long timeoutMs)` - Sends `CommitHead` to configured transports, waits for acknowledgment, and refreshes the active transport set used for the next block-building round.
- `endIteration(String blockHash, Long iterationId)` - Marks the end of an iteration for a specific block hash, storing the iteration ID for later use when committing the head.
- `newIteration(NewIteration newIteration)` - Sends a new iteration event to the currently active transports and returns a `CompletableFuture<Void>` that completes when the request is dispatched.
- `dispatchTransactions(SendTransactionsRequest sendTxRequest)` - Dispatches transaction data to all active transports and registers a tracked request per transaction. The returned futures are completed by `SubscribeResults` first and remain pending if unary recovery later returns `not_found`.
- `getTransactionResult(GetTransactionRequest transactionRequest)` - Called in the `evaluateTransactionPostProcessing` stage. It waits for the tracked request within the configured processing budget, starting unary `getTransaction` recovery lookups only after the initial stream-wait slice expires. The first concrete result from either the stream or unary lookup wins; unary `not_found` is non-terminal; `CredibleRejectionReason.PROCESSING_TIMEOUT` is returned only after the full processing window expires with no concrete result.
- `sendReorgRequest(ReorgRequest reorgRequest)` - Sends a reorganization request to all active transports and returns a list of `ReorgResponse` objects.
- `isActive()` - Returns a boolean indicating whether the strategy has any active transports available for communication.
- `setActive(boolean active)` - Sets the active state of the strategy.

### DefaultSidecarStrategy Implementation

The `ISidecarStrategy` interface is implemented by the `net.phylax.credible.strategy.DefaultSidecarStrategy` class, which uses the `ISidecarTransport` interface to communicate with the Credible Layer sidecar and evaluate transactions.

Key features of the DefaultSidecarStrategy:

- **Client-side Load Balancing**: Maintains separate lists of primary and fallback transports. If all primary transports fail during `newIteration`, it automatically switches to fallback transports.
- **Active Transport Management**: Dynamically updates the list of active transports based on successful responses. Transports that fail are automatically removed from the active pool.
- **Stream-first Completion**: `SubscribeResults` is the primary completion path for dispatched transactions, matching the `sidecar.proto` contract.
- **Unary Recovery Lookup**: `getTransactionResult` starts unary `getTransaction` lookups only after the initial stream wait expires. `not_found` means "no usable result yet" for that transport, not success and not terminal failure.
- **First Concrete Result Wins**: The first streamed or unary `TransactionResult` that contains a concrete result completes the tracked request.
- **Full-window Timeout Handling**: `CredibleRejectionReason.PROCESSING_TIMEOUT` is returned only when the configured processing window is fully exhausted without any concrete result.
- **Request Tracking**: Maintains pending transaction requests until terminal success, timeout, or terminal error so stream and unary recovery can coordinate on the same request lifecycle.

## Flow chart

```
BLOCK PRODUCTION STARTS
│
├─ ITERATION 1
│  │
│  ├─ traceStartBlock
│  │  └─ ISidecarStrategy.newIteration(NewIteration)
│  │     └─ ISidecarTransport.sendEvents(commitHead + newIteration)
│  │
│  ├─ Transaction Selection Loop (for each tx in mempool)
│  │  │
│  │  ├─ evaluateTransactionPreProcessing
│  │  │  └─ ISidecarStrategy.dispatchTransactions(SendTransactionsRequest)
│  │  │     ├─ ISidecarTransport.sendTransactions() → per active transport
│  │  │     └─ Registers pending request → waits for SubscribeResults
│  │  │
│  │  └─ evaluateTransactionPostProcessing
│  │     └─ ISidecarStrategy.getTransactionResult(GetTransactionRequest)
│  │        ├─ Waits for streamed TransactionResult for initial timeout slice
│  │        ├─ On stream timeout, starts ISidecarTransport.getTransaction() on active transports
│  │        ├─ Unary not_found keeps request pending
│  │        └─ First concrete result wins, or full processing window ends in PROCESSING_TIMEOUT
│  │
│  └─ traceEndBlock
│     └─ ISidecarStrategy.endIteration(blockHash, iterationId)
│        └─ Stores blockHash → iterationId mapping
│
├─ ITERATION 2
│  │
│  ├─ traceStartBlock → newIteration
│  ├─ Transaction Selection Loop → dispatchTransactions → getTransactionResult
│  └─ traceEndBlock → endIteration
│
├─ ITERATION N
│  │
│  ├─ [Same flow repeats until time expires or block full]
│  └─ ...
│
└─ BLOCK PRODUCTION ENDS

onBlockAdded event
│
└─ ISidecarStrategy.commitHead(CommitHead, timeoutMs)
   ├─ Retrieves iterationId from blockHash mapping
   ├─ Sets selectedIterationId in CommitHead
   └─ Refreshes the active transport set for the next iteration cycle

NEXT BLOCK PRODUCTION STARTS (cycle repeats)
```
