# Credible Layer Besu Plugin

This repository contains the code for the Hyperledger Besu plugin that integrates the Credible Layer.

## How to use

To use the plugin, follow these steps:

1. Build the plugin by running `./gradlew build` in the root directory of this repository.
2. Place the generated JAR file (`build/libs/credible-layer-besu-plugin-*.jar`) in the `plugins` directory of your Besu node.

## How it works

The Credible Layer Besu Plugin is designed to integrate the Credible Layer into the Besu node. It provides a transaction selection service that allows plugins to select transactions for block inclusion.

The plugin uses the BesuPlugin mechanism to interact with the Besu node. It implements the [TransactionSelectionService](https://github.com/hyperledger/besu/blob/main/plugin-api/src/main/java/org/hyperledger/besu/plugin/services/TransactionSelectionService.java) interface, which provides methods for creating a plugin transaction selector, selecting pending transactions during block creation, and registering plugin transaction selector factories.

The plugin implements two transports: HTTP JSON-RPC and GRPC. It sends transaction data to the Credible Layer for evaluation and receives the evaluation results back.

For more information on the internals of the BesuPlugin mechanism and the TransactionSelectionService, refer to the [DEVELOPER.md](DEVELOPER.md) file.

## Contributing

Contributions are welcome! If you find any issues or have suggestions for improvements, please open an issue or submit a pull request.