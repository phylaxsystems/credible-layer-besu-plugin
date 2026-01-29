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

- **DEPRECATED** `sendBlockEnv(SendBlockEnvRequest blockEnv)` - Sends the block environment to the Credible Layer sidecar and returns a `CompletableFuture<SendBlockEnvResponse>`.
- `sendTransactions(SendTransactionsRequest transactions)` - Sends transaction data to the Credible Layer sidecar for evaluation and returns a `CompletableFuture<SendTransactionsResponse>`.
- `getTransactions(GetTransactionsRequest transactions)` - Retrieves multiple transaction data from the Credible Layer sidecar based on the provided transaction request and returns a `CompletableFuture<GetTransactionsResponse>`.
- `getTransaction(GetTransactionRequest transactions)` - Retrieves a single transaction data from the Credible Layer sidecar based on the provided transaction request and returns a `CompletableFuture<GetTransactionResponse>`.
- `sendReorg(ReorgRequest reorgRequest)` - Sends a reorganization request to the Credible Layer sidecar and returns a `CompletableFuture<ReorgResponse>`.
- `sendEvents(SendEventsRequest events)` - Sends events to the Credible Layer sidecar, which can be `commitHead`, `newIteration` or `sendTransactions` and returns a `CompletableFuture<SendEventsResponse>` (generic *send** event).

## ISidecarStrategy

The `ISidecarStrategy` interface is defined in the `net.phylax.credible.strategy.ISidecarStrategy` class. It abstracts the orchestration of calls to the transport by providing methods that the TransactionSelectionPlugin uses.

The `ISidecarStrategy` interface includes the following methods:

- `setNewHead(String blockHash, CommitHead newHead)` - Sets a new head for a specific block hash, updating the selected iteration ID and storing it for the next iteration event.

- `endIteration(String blockHash, Long iterationId)` - Marks the end of an iteration for a specific block hash, storing the iteration ID for later use when committing the head.

- `newIteration(NewIteration newIteration)` - Sends a new iteration event to the Credible Layer sidecar and returns a `CompletableFuture<Void>` that completes when the request is processed. This method handles the block environment setup and implements fallback logic if primary transports fail. It also updates active transports based on successful responses.

- `dispatchTransactions(SendTransactionsRequest sendTxRequest)` - Dispatches transaction data to all active transports and returns a list of `CompletableFuture<GetTransactionResponse>` objects (one per transport). This method is called in the `evaluateTransactionPreProcessing` stage. It chains `sendTransactions` and `getTransaction` calls per transport, creating a non-blocking request pipeline. The futures are stored in a pending requests map for later retrieval.

- `getTransactionResult(GetTransactionRequest transactionRequest)` - Retrieves the result of transaction processing from the Credible Layer sidecar. This method is called in the `evaluateTransactionPostProcessing` stage. It awaits the futures from `dispatchTransactions` with a configurable timeout and returns a `Result<GetTransactionResponse, CredibleRejectionReason>` containing either the first successful response or a rejection reason. Implements a "race to success" pattern where the first successful transport response is used.

- `sendReorgRequest(ReorgRequest reorgRequest)` - Sends a reorganization request to all active transports and returns a list of `ReorgResponse` objects. This method waits for all `sendTransactions` futures to complete before sending the reorg request to ensure consistency.

- `isActive()` - Returns a boolean indicating whether the strategy has any active transports available for communication.

### DefaultSidecarStrategy Implementation

The `ISidecarStrategy` interface is implemented by the `net.phylax.credible.strategy.DefaultSidecarStrategy` class, which uses the `ISidecarTransport` interface to communicate with the Credible Layer sidecar and evaluate transactions.

Key features of the DefaultSidecarStrategy:

- **Client-side Load Balancing**: Maintains separate lists of primary and fallback transports. If all primary transports fail during `newIteration`, it automatically switches to fallback transports.

- **Active Transport Management**: Dynamically updates the list of active transports based on successful responses. Transports that fail are automatically removed from the active pool.

- **Non-blocking Pipeline**: The `dispatchTransactions` method chains `sendTransactions` and `getTransaction` calls per transport without blocking, allowing parallel processing across multiple sidecars.

- **Race to Success**: The `getTransactionResult` method uses an `anySuccessOf` pattern that completes as soon as the first transport returns a successful response, optimizing latency.

- **Timeout Handling**: Implements configurable timeouts for transaction processing. If all transports timeout or fail, the strategy marks itself as inactive and rejects subsequent transactions until it recovers.

- **Request Tracking**: Maintains internal maps to track pending transaction requests and `sendTransactions` futures, enabling proper coordination of reorg requests and result retrieval.

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
│  │  │     └─ ISidecarTransport.getTransaction() → chained per transport
│  │  │
│  │  └─ evaluateTransactionPostProcessing
│  │     └─ ISidecarStrategy.getTransactionResult(GetTransactionRequest)
│  │        └─ Awaits futures with timeout, returns first success
│  │           └─ Decision: Include tx in block or reject
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
└─ ISidecarStrategy.setNewHead(blockHash, CommitHead)
   ├─ Retrieves iterationId from blockHash mapping
   ├─ Sets selectedIterationId in CommitHead
   └─ Stored for next block's newIteration call

NEXT BLOCK PRODUCTION STARTS (cycle repeats)
```