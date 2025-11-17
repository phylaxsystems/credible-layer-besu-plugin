package net.phylax.credible.transport.grpc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import io.opentelemetry.api.OpenTelemetry;
import net.phylax.credible.metrics.CredibleMetricsRegistry;
import net.phylax.credible.metrics.SimpleMockMetricsSystem;
import net.phylax.credible.types.SidecarApiModels;
import sidecar.transport.v1.Sidecar;
import sidecar.transport.v1.SidecarTransportGrpc;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GrpcTransport using an in-process gRPC server
 */
public class GrpcTransportTest {
    private GrpcTransport transport;
    private TestSidecarService testService;
    private final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    /**
     * Test implementation of the SidecarTransport service
     */
    private static class TestSidecarService extends SidecarTransportGrpc.SidecarTransportImplBase {
        // Store last received requests for verification
        public Sidecar.BlockEnvEnvelope lastBlockEnvRequest;
        public Sidecar.SendTransactionsRequest lastSendTransactionsRequest;
        public Sidecar.GetTransactionsRequest lastGetTransactionsRequest;
        public Sidecar.GetTransactionRequest lastGetTransactionRequest;
        public Sidecar.ReorgRequest lastReorgRequest;

        // Configurable responses
        public boolean blockEnvAccepted = true;
        public String blockEnvMessage = "Block accepted";
        public int transactionsAcceptedCount = 0;
        public List<Sidecar.TransactionResult> transactionResults = new ArrayList<>();
        public List<String> notFoundTxHashes = new ArrayList<>();
        public boolean reorgAccepted = true;

        @Override
        public void sendBlockEnv(Sidecar.BlockEnvEnvelope request,
                                StreamObserver<Sidecar.BasicAck> responseObserver) {
            lastBlockEnvRequest = request;
            responseObserver.onNext(Sidecar.BasicAck.newBuilder()
                .setAccepted(blockEnvAccepted)
                .setMessage(blockEnvMessage)
                .build());
            responseObserver.onCompleted();
        }

        @Override
        public void sendTransactions(Sidecar.SendTransactionsRequest request,
                                     StreamObserver<Sidecar.SendTransactionsResponse> responseObserver) {
            lastSendTransactionsRequest = request;
            responseObserver.onNext(Sidecar.SendTransactionsResponse.newBuilder()
                .setAcceptedCount(transactionsAcceptedCount)
                .setRequestCount(request.getTransactionsCount())
                .setMessage("Transactions processed")
                .build());
            responseObserver.onCompleted();
        }

        @Override
        public void getTransactions(Sidecar.GetTransactionsRequest request,
                                   StreamObserver<Sidecar.GetTransactionsResponse> responseObserver) {
            lastGetTransactionsRequest = request;
            responseObserver.onNext(Sidecar.GetTransactionsResponse.newBuilder()
                .addAllResults(transactionResults)
                .addAllNotFound(notFoundTxHashes)
                .build());
            responseObserver.onCompleted();
        }

        @Override
        public void getTransaction(Sidecar.GetTransactionRequest request,
                                   StreamObserver<Sidecar.GetTransactionResponse> responseObserver) {
            lastGetTransactionRequest = request;
            responseObserver.onNext(Sidecar.GetTransactionResponse.newBuilder()
                .setResult(transactionResults.get(0))
                .build());
            responseObserver.onCompleted();
        }

        @Override
        public void reorg(Sidecar.ReorgRequest request,
                         StreamObserver<Sidecar.BasicAck> responseObserver) {
            lastReorgRequest = request;
            responseObserver.onNext(Sidecar.BasicAck.newBuilder()
                .setAccepted(reorgAccepted)
                .setMessage("Reorg processed")
                .build());
            responseObserver.onCompleted();
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        testService = new TestSidecarService();

        // Generate a unique in-process server name
        String serverName = InProcessServerBuilder.generateName();

        // Create and start an in-process server
        grpcCleanup.register(InProcessServerBuilder
            .forName(serverName)
            .directExecutor()
            .addService(testService)
            .build()
            .start());

        // Create an in-process channel
        ManagedChannel channel = grpcCleanup.register(
            InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build());

        var metrics = new CredibleMetricsRegistry(new SimpleMockMetricsSystem());
        
        // Create the transport with the in-process channel
        transport = new GrpcTransport(channel, channel, 5000, OpenTelemetry.noop(), metrics);
    }

    @AfterEach
    public void tearDown() {
        if (transport != null) {
            transport.close();
        }
    }

    @Test
    public void testSendBlockEnv() throws Exception {
        // Create a test block env request
        SidecarApiModels.BlockEnv blockEnvData = new SidecarApiModels.BlockEnv(
            12345L,                                      // number
            "0x1234567890123456789012345678901234567890", // beneficiary
            1234567890L,                                 // timestamp
            30000000L,                                   // gasLimit
            1000000000L,                                 // baseFee
            "0",                                         // difficulty
            "0x0000000000000000000000000000000000000000000000000000000000000000", // prevrandao
            new SidecarApiModels.BlobExcessGasAndPrice(0L, 1L) // blobExcessGasAndPrice
        );
        SidecarApiModels.SendBlockEnvRequest request = new SidecarApiModels.SendBlockEnvRequest(
            blockEnvData,
            "0xabcdef1234567890",
            10,
            200000L
        );

        // Send the request
        CompletableFuture<SidecarApiModels.SendBlockEnvResponse> future = transport.sendBlockEnv(request);
        SidecarApiModels.SendBlockEnvResponse response = future.get();

        // Verify the response
        assertTrue(response.getSuccess());
        assertEquals("accepted", response.getStatus());
        assertEquals("Block accepted", response.getMessage());

        // Verify the request was received correctly
        assertNotNull(testService.lastBlockEnvRequest);
        assertEquals(12345L, testService.lastBlockEnvRequest.getBlockEnv().getNumber());
        assertEquals("0x1234567890123456789012345678901234567890",
                    testService.lastBlockEnvRequest.getBlockEnv().getBeneficiary());
        assertEquals(10, testService.lastBlockEnvRequest.getNTransactions());
        assertEquals("0xabcdef1234567890", testService.lastBlockEnvRequest.getLastTxHash());
        assertTrue(testService.lastBlockEnvRequest.getBlockEnv().hasBlobExcessGasAndPrice());
        assertEquals(0L, testService.lastBlockEnvRequest.getBlockEnv().getBlobExcessGasAndPrice().getExcessBlobGas());
        assertEquals("1", testService.lastBlockEnvRequest.getBlockEnv().getBlobExcessGasAndPrice().getBlobGasprice());
    }

    @Test
    public void testSendBlockEnvRejected() throws Exception {
        // Configure service to reject
        testService.blockEnvAccepted = false;
        testService.blockEnvMessage = "Block rejected due to invalid data";

        SidecarApiModels.BlockEnv blockEnvData = new SidecarApiModels.BlockEnv(
            12345L, "0x1234567890123456789012345678901234567890",
            1234567890L, 30000000L, 1000000000L, "0",
            "0x0000000000000000000000000000000000000000000000000000000000000000",
            new SidecarApiModels.BlobExcessGasAndPrice(0L, 1L)
        );
        SidecarApiModels.SendBlockEnvRequest request = new SidecarApiModels.SendBlockEnvRequest(
            blockEnvData, "0x1234567890abcdef1234567890abcdef", 0, 1L
        );

        CompletableFuture<SidecarApiModels.SendBlockEnvResponse> future = transport.sendBlockEnv(request);
        SidecarApiModels.SendBlockEnvResponse response = future.get();

        assertFalse(response.getSuccess());
        assertEquals("failed", response.getStatus());
        assertEquals("Block rejected due to invalid data", response.getMessage());
    }

    @Test
    public void testSendTransactions() throws Exception {
        // Create test transactions
        List<SidecarApiModels.TransactionExecutionPayload> transactions = new ArrayList<>();

        SidecarApiModels.TxEnv txEnv = new SidecarApiModels.TxEnv();
        txEnv.setCaller("0xsender123");
        txEnv.setGasLimit(21000L);
        txEnv.setGasPrice(1_000_000_000L);
        txEnv.setKind("0xrecipient456");
        txEnv.setValue("1000000000000000000");
        txEnv.setData("0x");
        txEnv.setNonce(0L);
        txEnv.setChainId(1L);
        txEnv.setTxType((byte) 2);
        txEnv.setMaxFeePerBlobGas(25L);
        txEnv.setGasPriorityFee(2L);
        txEnv.setBlobHashes(List.of("0xblobhash"));

        SidecarApiModels.AccessListEntry accessListEntry = new SidecarApiModels.AccessListEntry(
            "0xaccess", List.of("0xkey1", "0xkey2"));
        txEnv.setAccessList(List.of(accessListEntry));

        SidecarApiModels.AuthorizationListEntry authorizationListEntry = new SidecarApiModels.AuthorizationListEntry();
        authorizationListEntry.setAddress("0xauth");
        authorizationListEntry.setV((byte) 1);
        authorizationListEntry.setR("0xr");
        authorizationListEntry.setS("0xs");
        authorizationListEntry.setChainId(1L);
        authorizationListEntry.setNonce(5L);
        txEnv.setAuthorizationList(List.of(authorizationListEntry));

        SidecarApiModels.TxExecutionId txExecutionId = new SidecarApiModels.TxExecutionId(
            12345L, 1L, "0xtxhash1"
        );
        transactions.add(new SidecarApiModels.TransactionExecutionPayload(
            txExecutionId, txEnv
        ));

        SidecarApiModels.SendTransactionsRequest request =
            new SidecarApiModels.SendTransactionsRequest(transactions);

        // Configure test service
        testService.transactionsAcceptedCount = 1;

        // Send the request
        CompletableFuture<SidecarApiModels.SendTransactionsResponse> future =
            transport.sendTransactions(request);
        SidecarApiModels.SendTransactionsResponse response = future.get();

        // Verify the response
        assertEquals("success", response.getStatus());
        assertEquals(1, response.getRequestCount());

        // Verify the request was received correctly
        assertNotNull(testService.lastSendTransactionsRequest);
        assertEquals(1, testService.lastSendTransactionsRequest.getTransactionsCount());
        assertEquals("0xtxhash1",
                    testService.lastSendTransactionsRequest.getTransactions(0).getTxExecutionId().getTxHash());

        Sidecar.TransactionEnv protoEnv =
            testService.lastSendTransactionsRequest.getTransactions(0).getTxEnv();
        assertEquals(2, protoEnv.getTxType());
        assertEquals("0xrecipient456", protoEnv.getKind());
        assertEquals("1000000000000000000", protoEnv.getValue());
        assertEquals("1000000000", protoEnv.getGasPrice());
        assertEquals("25", protoEnv.getMaxFeePerBlobGas());
        assertEquals("2", protoEnv.getGasPriorityFee());
        assertEquals(1, protoEnv.getBlobHashesCount());
        assertEquals("0xblobhash", protoEnv.getBlobHashes(0));
        assertEquals(1, protoEnv.getAccessListCount());
        assertEquals("0xaccess", protoEnv.getAccessList(0).getAddress());
        assertEquals(2, protoEnv.getAccessList(0).getStorageKeysCount());
        assertEquals(1, protoEnv.getAuthorizationListCount());
        assertEquals("0xauth", protoEnv.getAuthorizationList(0).getAddress());
        assertEquals("1", protoEnv.getAuthorizationList(0).getYParity());
        assertEquals("0xr", protoEnv.getAuthorizationList(0).getR());
        assertEquals("1", protoEnv.getAuthorizationList(0).getChainId());
        assertEquals(5L, protoEnv.getAuthorizationList(0).getNonce());
    }

    @Test
    public void testGetTransactions() throws Exception {
        // Configure test service with some results
        testService.transactionResults.add(
            Sidecar.TransactionResult.newBuilder()
                .setTxExecutionId(Sidecar.TxExecutionId.newBuilder()
                    .setBlockNumber(0L)
                    .setIterationId(0L)
                    .setTxHash("0xtxhash1")
                    .build())
                .setStatus("success")
                .setGasUsed(21000)
                .setError("")
                .build()
        );
        testService.notFoundTxHashes.add("0xtxhash2");

        // Create request with TxExecutionId
        SidecarApiModels.GetTransactionsRequest txReq = new SidecarApiModels.GetTransactionsRequest();
        txReq.setTxExecutionIds(List.of(
            new SidecarApiModels.TxExecutionId(1000L, 1L, "0xtxhash1"),
            new SidecarApiModels.TxExecutionId(1000L, 1L, "0xtxhash2")
        ));

        // Send the request
        CompletableFuture<SidecarApiModels.GetTransactionsResponse> future =
            transport.getTransactions(txReq);
        SidecarApiModels.GetTransactionsResponse response = future.get();

        // Verify the response
        assertEquals(1, response.getResults().size());
        assertEquals(1, response.getNotFound().size());

        SidecarApiModels.TransactionResult result = response.getResults().get(0);
        assertEquals("0xtxhash1", result.getTxExecutionId().getTxHash());
        assertEquals("success", result.getStatus());
        assertEquals(21000, result.getGasUsed());

        assertEquals("0xtxhash2", response.getNotFound().get(0));

        // Verify the request was received correctly
        assertNotNull(testService.lastGetTransactionsRequest);
        assertEquals(2, testService.lastGetTransactionsRequest.getTxExecutionIdCount());
    }

    @Test
    public void testGetTransaction() throws Exception {
        // Configure test service with some results
        testService.transactionResults.add(
            Sidecar.TransactionResult.newBuilder()
                .setTxExecutionId(Sidecar.TxExecutionId.newBuilder()
                    .setBlockNumber(0L)
                    .setIterationId(0L)
                    .setTxHash("0xtxhash1")
                    .build())
                .setStatus("success")
                .setGasUsed(21000)
                .setError("")
                .build()
        );

        // Create request with TxExecutionId
        SidecarApiModels.GetTransactionRequest txReq = new SidecarApiModels.GetTransactionRequest(1000L, 1L, "0xtxhash1");

        // Send the request
        CompletableFuture<SidecarApiModels.GetTransactionResponse> future =
            transport.getTransaction(txReq);
        SidecarApiModels.GetTransactionResponse response = future.get();

        // Verify the response
        assertTrue(response.getResult() != null);

        SidecarApiModels.TransactionResult result = response.getResult();
        assertEquals("0xtxhash1", result.getTxExecutionId().getTxHash());
        assertEquals("success", result.getStatus());
        assertEquals(21000, result.getGasUsed());

        // Verify the request was received correctly
        assertNotNull(testService.lastGetTransactionRequest);
    }

    @Test
    public void testSendReorg() throws Exception {
        // Create reorg request with TxExecutionId
        SidecarApiModels.ReorgRequest request =
            new SidecarApiModels.ReorgRequest(12345L, 1L, "0xremovedtxhash");

        // Send the request
        CompletableFuture<SidecarApiModels.ReorgResponse> future = transport.sendReorg(request);
        SidecarApiModels.ReorgResponse response = future.get();

        // Verify the response
        assertTrue(response.getSuccess());
        assertNull(response.getError());

        // Verify the request was received correctly
        assertNotNull(testService.lastReorgRequest);
        assertEquals("0xremovedtxhash", testService.lastReorgRequest.getTxExecutionId().getTxHash());
        assertEquals(12345L, testService.lastReorgRequest.getTxExecutionId().getBlockNumber());
        assertEquals(1L, testService.lastReorgRequest.getTxExecutionId().getIterationId());
    }

    @Test
    public void testModelConversionsAreReversible() throws Exception {
        // Test that converting POJO → Proto → POJO maintains data integrity
        SidecarApiModels.BlockEnv blockEnvData = new SidecarApiModels.BlockEnv(
            99999L, "0xabcd", 9876543210L, 15000000L, 2000000000L, "12345",
            "0x1111111111111111111111111111111111111111111111111111111111111111",
            new SidecarApiModels.BlobExcessGasAndPrice(100L, 50L)
        );
        SidecarApiModels.SendBlockEnvRequest originalRequest = new SidecarApiModels.SendBlockEnvRequest(
            blockEnvData, "0xlasttx", 5, 1L
        );

        // Convert to proto
        Sidecar.BlockEnvEnvelope protoRequest =
            GrpcModelConverter.toProtoBlockEnvEnvelope(originalRequest);

        // Verify proto has correct values
        assertEquals(99999L, protoRequest.getBlockEnv().getNumber());
        assertEquals("0xabcd", protoRequest.getBlockEnv().getBeneficiary());
        assertEquals(5, protoRequest.getNTransactions());
        assertEquals("0xlasttx", protoRequest.getLastTxHash());

        // Send through transport and verify round-trip works
        testService.blockEnvAccepted = true;
        CompletableFuture<SidecarApiModels.SendBlockEnvResponse> future =
            transport.sendBlockEnv(originalRequest);
        SidecarApiModels.SendBlockEnvResponse response = future.get();

        assertTrue(response.getSuccess());
        assertNotNull(testService.lastBlockEnvRequest);
        assertEquals(originalRequest.getBlockEnv().getNumber(),
                    testService.lastBlockEnvRequest.getBlockEnv().getNumber());
    }
}
