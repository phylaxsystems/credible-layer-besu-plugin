package net.phylax.credible.types;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.phylax.credible.types.SidecarApiModels.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ModelSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testCommitHeadReqItemSerialization() throws Exception {
        // Create a CommitHeadReqItem
        CommitHead commitHead = new CommitHead(
            "0x2222222222222222222222222222222222222222222222222222222222222222",
            100,
            12346L,
            6L
        );
        CommitHeadReqItem item = new CommitHeadReqItem(commitHead);

        // Serialize to JSON
        String json = objectMapper.writeValueAsString(item);

        // Verify the structure contains the expected fields
        assertTrue(json.contains("\"commit_head\""));
        assertTrue(json.contains("\"last_tx_hash\""));
        assertTrue(json.contains("\"n_transactions\""));
        assertTrue(json.contains("\"block_number\""));
        assertTrue(json.contains("\"selected_iteration_id\""));
        assertTrue(json.contains("0x2222222222222222222222222222222222222222222222222222222222222222"));
        assertTrue(json.contains("100"));
        assertTrue(json.contains("12346"));
        assertTrue(json.contains("6"));
    }

    @Test
    void testCommitHeadReqItemDeserialization() throws Exception {
        String json = """
            {
                "commit_head": {
                    "last_tx_hash": "0x2222222222222222222222222222222222222222222222222222222222222222",
                    "n_transactions": 100,
                    "block_number": 12346,
                    "selected_iteration_id": 6
                }
            }
            """;

        // Deserialize from JSON
        CommitHeadReqItem item = objectMapper.readValue(json, CommitHeadReqItem.class);

        // Verify the deserialized object
        assertNotNull(item);
        assertNotNull(item.getCommitHead());
        assertEquals("0x2222222222222222222222222222222222222222222222222222222222222222",
            item.getCommitHead().getLastTxHash());
        assertEquals(100, item.getCommitHead().getNTransactions());
        assertEquals(12346L, item.getCommitHead().getBlockNumber());
        assertEquals(6L, item.getCommitHead().getSelectedIterationId());
    }

    @Test
    void testNewIterationReqItemSerialization() throws Exception {
        // Create BlockEnv
        BlockEnv blockEnv = new BlockEnv(
            12346L,
            "0x742d35Cc6634C0532925a3b844B9c7e07e3E23eF4",
            1625150405L,
            30000000L,
            1000000000L,
            "0x0",
            "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdff",
            null
        );

        // Create NewIteration
        NewIteration newIteration = new NewIteration(7L, blockEnv);

        // Create NewIterationReqItem
        NewIterationReqItem item = new NewIterationReqItem(newIteration);

        // Serialize to JSON
        String json = objectMapper.writeValueAsString(item);

        // Verify the structure
        assertTrue(json.contains("\"new_iteration\""));
        assertTrue(json.contains("\"iteration_id\""));
        assertTrue(json.contains("\"block_env\""));
        assertTrue(json.contains("\"number\""));
        assertTrue(json.contains("\"beneficiary\""));
        assertTrue(json.contains("\"timestamp\""));
        assertTrue(json.contains("\"gas_limit\""));
        assertTrue(json.contains("\"basefee\""));
        assertTrue(json.contains("\"difficulty\""));
        assertTrue(json.contains("\"prevrandao\""));
        assertTrue(json.contains("7"));
        assertTrue(json.contains("12346"));
        assertTrue(json.contains("0x742d35Cc6634C0532925a3b844B9c7e07e3E23eF4"));
    }

    @Test
    void testNewIterationReqItemDeserialization() throws Exception {
        String json = """
            {
                "new_iteration": {
                    "iteration_id": 7,
                    "block_env": {
                        "number": 12346,
                        "beneficiary": "0x742d35Cc6634C0532925a3b844B9c7e07e3E23eF4",
                        "timestamp": 1625150405,
                        "gas_limit": 30000000,
                        "basefee": 1000000000,
                        "difficulty": "0x0",
                        "prevrandao": "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdff"
                    }
                }
            }
            """;

        // Deserialize from JSON
        NewIterationReqItem item = objectMapper.readValue(json, NewIterationReqItem.class);

        // Verify the deserialized object
        assertNotNull(item);
        assertNotNull(item.getNewIteration());
        assertEquals(7L, item.getNewIteration().getIterationId());
        assertNotNull(item.getNewIteration().getBlockEnv());
        assertEquals(12346L, item.getNewIteration().getBlockEnv().getNumber());
        assertEquals("0x742d35Cc6634C0532925a3b844B9c7e07e3E23eF4",
            item.getNewIteration().getBlockEnv().getBeneficiary());
        assertEquals(1625150405L, item.getNewIteration().getBlockEnv().getTimestamp());
        assertEquals(30000000L, item.getNewIteration().getBlockEnv().getGasLimit());
        assertEquals(1000000000L, item.getNewIteration().getBlockEnv().getBaseFee());
        assertEquals("0x0", item.getNewIteration().getBlockEnv().getDifficulty());
        assertEquals("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdff",
            item.getNewIteration().getBlockEnv().getPrevrandao());
    }

    @Test
    void testTransactionReqItemSerialization() throws Exception {
        // Create TxExecutionId
        TxExecutionId txExecutionId = new TxExecutionId(
            12346L,
            7L,
            "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
            0L
        );

        // Create TxEnv
        List<AccessListEntry> accessList = new ArrayList<>();
        AccessListEntry entry = new AccessListEntry(
            "0x742d35Cc6634C0532925a3b844B9c7e07e3E23eF",
            Arrays.asList("0x0000000000000000000000000000000000000000000000000000000000000001")
        );
        accessList.add(entry);

        TxEnv txEnv = new TxEnv(
            "0x742d35Cc6634C0532925a3b844B9c7e07e3E23eF",  // caller
            21000L,  // gasLimit
            1000000000L,  // gasPrice
            "0x742d35Cc6634C0532925a3b844B9c7e07e3E23eF4",  // kind
            "0x0",  // value
            "0x",  // data
            1L,  // nonce
            1L,  // chainId
            accessList,
            (byte) 2,  // txType
            0L,  // maxFeePerBlobGas
            null,  // gasPriorityFee
            new ArrayList<>(),  // blobHashes
            new ArrayList<>()  // authorizationList
        );

        // Create TransactionExecutionPayload
        TransactionExecutionPayload payload = new TransactionExecutionPayload(txExecutionId, txEnv, "0x123456789");

        // Create TransactionReqItem
        TransactionReqItem item = new TransactionReqItem(payload);

        // Serialize to JSON
        String json = objectMapper.writeValueAsString(item);

        // Verify the structure
        assertTrue(json.contains("\"transaction\""));
        assertTrue(json.contains("\"tx_execution_id\""));
        assertTrue(json.contains("\"tx_env\""));
        assertTrue(json.contains("\"block_number\""));
        assertTrue(json.contains("\"iteration_id\""));
        assertTrue(json.contains("\"tx_hash\""));
        assertTrue(json.contains("\"caller\""));
        assertTrue(json.contains("\"gas_limit\""));
        assertTrue(json.contains("12346"));
        assertTrue(json.contains("7"));
    }

    @Test
    void testTransactionReqItemDeserialization() throws Exception {
        String json = """
            {
                "transaction": {
                    "tx_execution_id": {
                        "block_number": 12346,
                        "iteration_id": 7,
                        "tx_hash": "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
                    },
                    "tx_env": {
                        "tx_type": 2,
                        "caller": "0x742d35Cc6634C0532925a3b844B9c7e07e3E23eF",
                        "gas_limit": 21000,
                        "gas_price": 1000000000,
                        "kind": "0x742d35Cc6634C0532925a3b844B9c7e07e3E23eF4",
                        "value": "0x0",
                        "data": "0x",
                        "nonce": 1,
                        "chain_id": 1,
                        "access_list": [
                            {
                                "address": "0x742d35Cc6634C0532925a3b844B9c7e07e3E23eF",
                                "storage_keys": ["0x0000000000000000000000000000000000000000000000000000000000000001"]
                            }
                        ],
                        "max_fee_per_blob_gas": 0,
                        "blob_hashes": [],
                        "authorization_list": []
                    }
                }
            }
            """;

        // Deserialize from JSON
        TransactionReqItem item = objectMapper.readValue(json, TransactionReqItem.class);

        // Verify the deserialized object
        assertNotNull(item);
        assertNotNull(item.getTransaction());
        assertNotNull(item.getTransaction().getTxExecutionId());
        assertEquals(12346L, item.getTransaction().getTxExecutionId().getBlockNumber());
        assertEquals(7L, item.getTransaction().getTxExecutionId().getIterationId());
        assertEquals("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
            item.getTransaction().getTxExecutionId().getTxHash());

        assertNotNull(item.getTransaction().getTxEnv());
        assertEquals("0x742d35Cc6634C0532925a3b844B9c7e07e3E23eF",
            item.getTransaction().getTxEnv().getCaller());
        assertEquals(21000L, item.getTransaction().getTxEnv().getGasLimit());
        assertEquals(1000000000L, item.getTransaction().getTxEnv().getGasPrice());
        assertEquals((byte) 2, item.getTransaction().getTxEnv().getTxType());
    }

    @Test
    void testSendEventsRequestSerialization() throws Exception {
        CommitHead commitHead = new CommitHead(
            "0x2222222222222222222222222222222222222222222222222222222222222222",
            100,
            12346L,
            6L
        );
        CommitHeadReqItem commitHeadItem = new CommitHeadReqItem(commitHead);

        BlockEnv blockEnv = new BlockEnv(
            12346L,
            "0x742d35Cc6634C0532925a3b844B9c7e07e3E23eF4",
            1625150405L,
            30000000L,
            1000000000L,
            "0x0",
            "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdff",
            null
        );
        NewIteration newIteration = new NewIteration(7L, blockEnv);
        NewIterationReqItem newIterationItem = new NewIterationReqItem(newIteration);

        TxExecutionId txExecutionId = new TxExecutionId(12346L, 7L, "0x1234567890abcdef", 0L);
        TxEnv txEnv = new TxEnv(
            "0x742d35Cc6634C0532925a3b844B9c7e07e3E23eF",
            21000L,
            1000000000L,
            "0x742d35Cc6634C0532925a3b844B9c7e07e3E23eF4",
            "0x0",
            "0x",
            1L,
            1L,
            new ArrayList<>(),
            (byte) 2,
            0L,
            null,
            new ArrayList<>(),
            new ArrayList<>()
        );
        TransactionExecutionPayload payload = new TransactionExecutionPayload(txExecutionId, txEnv, "0x123456789");
        TransactionReqItem transactionItem = new TransactionReqItem(payload);

        // Create SendEventsRequest with all three items
        List<SendEventsRequestItem> events = Arrays.asList(
            commitHeadItem,
            newIterationItem,
            transactionItem
        );
        SendEventsRequest request = new SendEventsRequest(events);

        // Serialize to JSON
        String json = objectMapper.writeValueAsString(request);

        // Verify the structure
        assertTrue(json.contains("\"events\""));
        assertTrue(json.contains("\"commit_head\""));
        assertTrue(json.contains("\"new_iteration\""));
        assertTrue(json.contains("\"transaction\""));
        assertTrue(json.contains("\"last_tx_hash\""));
        assertTrue(json.contains("\"iteration_id\""));
        assertTrue(json.contains("\"tx_execution_id\""));
    }

    @Test
    void testSendEventsRequestDeserialization() throws Exception {
        String json = """
            {
                "events": [
                    {
                        "commit_head": {
                            "last_tx_hash": "0x2222222222222222222222222222222222222222222222222222222222222222",
                            "n_transactions": 100,
                            "block_number": 12346,
                            "selected_iteration_id": 6
                        }
                    },
                    {
                        "new_iteration": {
                            "iteration_id": 7,
                            "block_env": {
                                "number": 12346,
                                "beneficiary": "0x742d35Cc6634C0532925a3b844B9c7e07e3E23eF4",
                                "timestamp": 1625150405,
                                "gas_limit": 30000000,
                                "basefee": 1000000000,
                                "difficulty": "0x0",
                                "prevrandao": "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdff"
                            }
                        }
                    },
                    {
                        "transaction": {
                            "tx_execution_id": {
                                "block_number": 12346,
                                "iteration_id": 7,
                                "tx_hash": "0x1234567890abcdef"
                            },
                            "tx_env": {
                                "tx_type": 2,
                                "caller": "0x742d35Cc6634C0532925a3b844B9c7e07e3E23eF",
                                "gas_limit": 21000,
                                "gas_price": 1000000000,
                                "kind": "0x742d35Cc6634C0532925a3b844B9c7e07e3E23eF4",
                                "value": "0x0",
                                "data": "0x",
                                "nonce": 1,
                                "chain_id": 1,
                                "access_list": [],
                                "max_fee_per_blob_gas": 0,
                                "blob_hashes": [],
                                "authorization_list": []
                            }
                        }
                    }
                ]
            }
            """;

        // Deserialize from JSON
        SendEventsRequest request = objectMapper.readValue(json, SendEventsRequest.class);

        // Verify the deserialized object
        assertNotNull(request);
        assertNotNull(request.getEvents());
        assertEquals(3, request.getEvents().size());

        // Verify first item is CommitHeadReqItem
        assertTrue(request.getEvents().get(0) instanceof CommitHeadReqItem);
        CommitHeadReqItem commitHeadItem = (CommitHeadReqItem) request.getEvents().get(0);
        assertEquals("0x2222222222222222222222222222222222222222222222222222222222222222",
            commitHeadItem.getCommitHead().getLastTxHash());
        assertEquals(100, commitHeadItem.getCommitHead().getNTransactions());

        // Verify second item is NewIterationReqItem
        assertTrue(request.getEvents().get(1) instanceof NewIterationReqItem);
        NewIterationReqItem newIterationItem = (NewIterationReqItem) request.getEvents().get(1);
        assertEquals(7L, newIterationItem.getNewIteration().getIterationId());
        assertEquals(12346L, newIterationItem.getNewIteration().getBlockEnv().getNumber());

        // Verify third item is TransactionReqItem
        assertTrue(request.getEvents().get(2) instanceof TransactionReqItem);
        TransactionReqItem transactionItem = (TransactionReqItem) request.getEvents().get(2);
        assertEquals(12346L, transactionItem.getTransaction().getTxExecutionId().getBlockNumber());
        assertEquals(7L, transactionItem.getTransaction().getTxExecutionId().getIterationId());
    }

    @Test
    void testRoundTripSerializationCommitHeadReqItem() throws Exception {
        // Create original object
        CommitHead commitHead = new CommitHead(
            "0x2222222222222222222222222222222222222222222222222222222222222222",
            100,
            12346L,
            6L
        );
        CommitHeadReqItem original = new CommitHeadReqItem(commitHead);

        // Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        CommitHeadReqItem roundTripped = objectMapper.readValue(json, CommitHeadReqItem.class);

        // Verify equality
        assertEquals(original.getCommitHead().getLastTxHash(),
            roundTripped.getCommitHead().getLastTxHash());
        assertEquals(original.getCommitHead().getNTransactions(),
            roundTripped.getCommitHead().getNTransactions());
        assertEquals(original.getCommitHead().getBlockNumber(),
            roundTripped.getCommitHead().getBlockNumber());
        assertEquals(original.getCommitHead().getSelectedIterationId(),
            roundTripped.getCommitHead().getSelectedIterationId());
    }

    @Test
    void testRoundTripSerializationNewIterationReqItem() throws Exception {
        // Create original object
        BlockEnv blockEnv = new BlockEnv(
            12346L,
            "0x742d35Cc6634C0532925a3b844B9c7e07e3E23eF4",
            1625150405L,
            30000000L,
            1000000000L,
            "0x0",
            "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdff",
            new BlobExcessGasAndPrice(1000L, 2000L)
        );
        NewIteration newIteration = new NewIteration(7L, blockEnv);
        NewIterationReqItem original = new NewIterationReqItem(newIteration);

        // Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        NewIterationReqItem roundTripped = objectMapper.readValue(json, NewIterationReqItem.class);

        // Verify equality
        assertEquals(original.getNewIteration().getIterationId(),
            roundTripped.getNewIteration().getIterationId());
        assertEquals(original.getNewIteration().getBlockEnv().getNumber(),
            roundTripped.getNewIteration().getBlockEnv().getNumber());
        assertEquals(original.getNewIteration().getBlockEnv().getBeneficiary(),
            roundTripped.getNewIteration().getBlockEnv().getBeneficiary());
        assertEquals(original.getNewIteration().getBlockEnv().getTimestamp(),
            roundTripped.getNewIteration().getBlockEnv().getTimestamp());
        assertNotNull(roundTripped.getNewIteration().getBlockEnv().getBlobExcessGasAndPrice());
        assertEquals(1000L,
            roundTripped.getNewIteration().getBlockEnv().getBlobExcessGasAndPrice().getExcessBlobGas());
        assertEquals(2000L,
            roundTripped.getNewIteration().getBlockEnv().getBlobExcessGasAndPrice().getBlobGasPrice());
    }

    @Test
    void testRoundTripSerializationTransactionReqItem() throws Exception {
        // Create original object
        TxExecutionId txExecutionId = new TxExecutionId(12346L, 7L, "0x1234567890abcdef", 0L);
        TxEnv txEnv = new TxEnv(
            "0x742d35Cc6634C0532925a3b844B9c7e07e3E23eF",
            21000L,
            1000000000L,
            "0x742d35Cc6634C0532925a3b844B9c7e07e3E23eF4",
            "0x0",
            "0x",
            1L,
            1L,
            new ArrayList<>(),
            (byte) 2,
            0L,
            null,
            new ArrayList<>(),
            new ArrayList<>()
        );
        TransactionExecutionPayload payload = new TransactionExecutionPayload(txExecutionId, txEnv, "0x123456789");
        TransactionReqItem original = new TransactionReqItem(payload);

        // Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        TransactionReqItem roundTripped = objectMapper.readValue(json, TransactionReqItem.class);

        // Verify equality
        assertEquals(original.getTransaction().getTxExecutionId().getBlockNumber(),
            roundTripped.getTransaction().getTxExecutionId().getBlockNumber());
        assertEquals(original.getTransaction().getTxExecutionId().getIterationId(),
            roundTripped.getTransaction().getTxExecutionId().getIterationId());
        assertEquals(original.getTransaction().getTxExecutionId().getTxHash(),
            roundTripped.getTransaction().getTxExecutionId().getTxHash());
        assertEquals(original.getTransaction().getTxEnv().getCaller(),
            roundTripped.getTransaction().getTxEnv().getCaller());
        assertEquals(original.getTransaction().getTxEnv().getGasLimit(),
            roundTripped.getTransaction().getTxEnv().getGasLimit());
    }
}
