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

## Communication with the Credible Layer

The Credible Layer Besu Plugin communicates with the Credible Layer using JSON-RPC over HTTP. It sends transaction data to the Credible Layer for evaluation and receives the evaluation results back.

The communication is handled by the `net.phylax.credible.transport.jsonrpc.JsonRpcTransport` class. The plugin sends JSON-RPC requests to the Credible Layer and receives JSON-RPC responses.

## ISidecarTransport

The ISidecarTransport interface is defined in the `net.phylax.credible.transport.ISidecarTransport` class. It provides methods for communication with the Credible Layer sidecar, which is a separate process that runs alongside the Besu node.

The ISidecarTransport interface includes the following methods:

- sendTransactions(SendTransactionsRequest transactions) - Sends transaction data to the Credible Layer sidecar for evaluation.
- getTransactions(List txHashes) - Retrieves transaction data from the Credible Layer sidecar based on the provided transaction hashes.
- sendBLockEnv(BlockEnv block) - Sends the processed BlockEnv to the sidecar

## ISidecarStrategy

The `ISidecarStrategy` interface is defined in the `net.phylax.credible.strategy.ISidecarStrategy` class. It abstracts the orchestration of calls to the transport by providing methods that the TransactionSelectionPlugin uses.

The `ISidecarStrategy` interface includes the following methods:

- `sendBlockEnv(SendBlockEnvRequest blockEnvRequest)`: Sends the block environment request to the Credible Layer sidecar and returns a `CompletableFuture` that completes when the request is processed

- `dispatchTransactions(SendTransactionsRequest sendTxRequest)`: Dispatches the provided transaction request to the Credible Layer sidecar and returns a list of `CompletableFuture` objects; this method is called in the `evaluateTransactionPreProcessing` stage and it returns awaitable futures from each of the sidecar endpoints

- `handleTransportResponses(List<CompletableFuture<GetTransactionsResponse>> futures)`: Handles the responses received from the Credible Layer sidecar (awaits for the result) and returns a list of `TransactionResult` objects; this method is called in the `evaluateTransactionPostProcessing` stage where it handles the logic of whether the transaction should be included or not

The `ISidecarStrategy` interface is implemented by the `net.phylax.credible.strategy.DefaultSidecarStrategy` class, which uses the `ISidecarTransport` interface to communicate with the Credible Layer sidecar and evaluate transactions. It implements a client-side load balancing mechanism for fallbacks