package net.phylax.credible.transport.jsonrpc;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.ConstructorDetector;
import com.fasterxml.jackson.databind.json.JsonMapper;

import net.phylax.credible.transport.ISidecarTransport;
import net.phylax.credible.types.SidecarApiModels.CredibleLayerMethods;
import net.phylax.credible.types.SidecarApiModels.GetTransactionsResponse;
import net.phylax.credible.types.SidecarApiModels.ReorgRequest;
import net.phylax.credible.types.SidecarApiModels.ReorgResponse;
import net.phylax.credible.types.SidecarApiModels.SendBlockEnvRequest;
import net.phylax.credible.types.SidecarApiModels.SendBlockEnvResponse;
import net.phylax.credible.types.SidecarApiModels.SendTransactionsRequest;
import net.phylax.credible.types.SidecarApiModels.SendTransactionsResponse;
import okhttp3.Authenticator;
import okhttp3.ConnectionPool;
import okhttp3.CookieJar;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Sidecar JSON RPC client
 * Requires dependencies: okhttp3 and jackson-databind
 */
public class JsonRpcTransport implements ISidecarTransport {
    private static final Logger LOG = LoggerFactory.getLogger(JsonRpcTransport.class);
    
    // JSON RPC Request class
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JsonRpcRequest {
        @JsonProperty("jsonrpc")
        private String jsonrpc = "2.0";
        
        @JsonProperty("method")
        private String method;
        
        @JsonProperty("params")
        private Object params;
        
        @JsonProperty("id")
        private String id;
        
        public JsonRpcRequest() {}
        
        @JsonCreator
        public JsonRpcRequest(@JsonProperty("method") String method, @JsonProperty("params") Object params, @JsonProperty("id") String id) {
            this.method = method;
            this.params = params;
            this.id = id;
        }
        
        // Getters and setters
        public String getJsonrpc() { return jsonrpc; }
        public void setJsonrpc(String jsonrpc) { this.jsonrpc = jsonrpc; }
        
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        
        public Object getParams() { return params; }
        public void setParams(Object params) { this.params = params; }
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }
    
    // JSON RPC Response class
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JsonRpcResponse<T> {
        @JsonProperty("jsonrpc")
        private String jsonrpc;
        
        @JsonProperty("result")
        private T result;
        
        @JsonProperty("error")
        private JsonRpcError error;
        
        @JsonProperty("id")
        private String id;
        
        public JsonRpcResponse() {}

        // Getters and setters
        public String getJsonrpc() { return jsonrpc; }
        public void setJsonrpc(String jsonrpc) { this.jsonrpc = jsonrpc; }
        
        public T getResult() { return result; }
        public void setResult(T result) { this.result = result; }
        
        public JsonRpcError getError() { return error; }
        public void setError(JsonRpcError error) { this.error = error; }
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public boolean hasError() { return error != null; }
    }
    
    // JSON RPC Error class
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JsonRpcError {
        @JsonProperty("code")
        private int code;
        
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("data")
        private Object data;
        
        @JsonCreator
        public JsonRpcError() {}
        
        // Getters and setters
        public int getCode() { return code; }
        public void setCode(int code) { this.code = code; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public Object getData() { return data; }
        public void setData(Object data) { this.data = data; }
        
        @Override
        public String toString() {
            return String.format("JsonRpcError{code=%d, message='%s', data=%s}", code, message, data);
        }
    }
    
    // JSON RPC Exception class
    public static class JsonRpcException extends Exception {
        private final JsonRpcError error;
        
        public JsonRpcException(JsonRpcError error) {
            super(error.getMessage());
            this.error = error;
        }
        
        public JsonRpcException(String message) {
            super(message);
            this.error = null;
        }
        
        public JsonRpcException(String message, Throwable cause) {
            super(message, cause);
            this.error = null;
        }
        
        public JsonRpcError getError() { return error; }
    }
    
    // Main Client Implementation
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    
    public JsonRpcTransport(String baseUrl) {
        this(baseUrl, createDefaultHttpClient());
    }
    
    public JsonRpcTransport(String baseUrl, OkHttpClient httpClient) {
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.objectMapper = JsonMapper.builder()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
            .enable(MapperFeature.USE_STD_BEAN_NAMING)
            .constructorDetector(ConstructorDetector.DEFAULT)
            .build();
    }
    
    private static OkHttpClient createDefaultHttpClient() {
        return new OkHttpClient.Builder()
                .connectionPool(new ConnectionPool(100, 2, TimeUnit.MINUTES))
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(false)
                .followSslRedirects(false)
                .cookieJar(CookieJar.NO_COOKIES)
                .build();
    }
    
    // Synchronous call with generic result type
    public <T> T call(String method, Object params, TypeReference<T> resultType) throws JsonRpcException {
        JsonRpcResponse<T> response = callForResponse(method, params, resultType);
        
        if (response.hasError()) {
            throw new JsonRpcException(response.getError());
        }
        
        return response.getResult();
    }
    
    // Synchronous call with Class result type
    public <T> T call(String method, Object params, Class<T> resultClass) throws JsonRpcException {
        JsonRpcResponse<T> response = callForResponse(method, params, resultClass);
        
        if (response.hasError()) {
            throw new JsonRpcException(response.getError());
        }
        
        return response.getResult();
    }
    
    // Synchronous call returning raw response with TypeReference
    public <T> JsonRpcResponse<T> callForResponse(String method, Object params, TypeReference<T> resultType) throws JsonRpcException {
        try {
            String requestId = UUID.randomUUID().toString();
            JsonRpcRequest request = new JsonRpcRequest(method, params, requestId);
            
            String requestJson = objectMapper.writeValueAsString(request);
            LOG.trace("Request ID: {}, body: {}", requestId, requestJson);

            RequestBody body = RequestBody.create(requestJson, JSON);
            
            Request httpRequest = new Request.Builder()
                    .url(baseUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw new JsonRpcException("HTTP error: " + response.code() + " " + response.message());
                }
                
                if (response.body() == null) {
                    throw new JsonRpcException("Empty response body");
                }
                
                String responseBody = response.body().string();
                LOG.trace("Response ID: {}, body: {}", requestId, responseBody);

                // Create JavaType from TypeReference for proper type handling
                JavaType responseType = objectMapper.getTypeFactory()
                    .constructParametricType(JsonRpcResponse.class, 
                                           objectMapper.getTypeFactory().constructType(resultType));
                
                // Parse directly to the typed response
                JsonRpcResponse<T> typedResponse = objectMapper.readValue(responseBody, responseType);
                
                return typedResponse;
            }
        } catch (IOException e) {
            throw new JsonRpcException("Network error", e);
        }
    }
    
    // Overload for Class-based result type
    public <T> JsonRpcResponse<T> callForResponse(String method, Object params, Class<T> resultClass) throws JsonRpcException {
        try {
            String requestId = UUID.randomUUID().toString();
            JsonRpcRequest request = new JsonRpcRequest(method, params, requestId);
            
            String requestJson = objectMapper.writeValueAsString(request);
            LOG.trace("Request ID: {}, body: {}", requestId, requestJson);

            RequestBody body = RequestBody.create(requestJson, JSON);
            
            Request httpRequest = new Request.Builder()
                    .url(baseUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw new JsonRpcException("HTTP error: " + response.code() + " " + response.message());
                }
                
                if (response.body() == null) {
                    throw new JsonRpcException("Empty response body");
                }
                
                String responseBody = response.body().string();
                LOG.trace("Response ID: {}, body: {}", requestId, responseBody);
                
                // Create JavaType for Class-based result type
                JavaType responseType = objectMapper.getTypeFactory()
                    .constructParametricType(JsonRpcResponse.class, resultClass);
                
                // Parse directly to the typed response
                JsonRpcResponse<T> typedResponse = objectMapper.readValue(responseBody, responseType);
                
                return typedResponse;
            }
        } catch (IOException e) {
            throw new JsonRpcException("Network error", e);
        }
    }
    
    // Asynchronous call
    public <T> CompletableFuture<T> callAsync(String method, Object params, TypeReference<T> resultType){
        return CompletableFuture.supplyAsync(() -> {
            try {
                return call(method, params, resultType);
            } catch (JsonRpcException e) {
                throw new CompletionException(e);
            }
        });
    }
    
    // Asynchronous call with Class result type
    public <T> CompletableFuture<T> callAsync(String method, Object params, Class<T> resultClass) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return call(method, params, resultClass);
            } catch (JsonRpcException e) {
                throw new CompletionException(e);
            }
        });
    }
    
    // Batch call support
    public List<JsonRpcResponse<Object>> batchCall(List<JsonRpcRequest> requests) throws JsonRpcException {
        try {
            String requestJson = objectMapper.writeValueAsString(requests);
            RequestBody body = RequestBody.create(requestJson, JSON);
            
            Request httpRequest = new Request.Builder()
                    .url(baseUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw new JsonRpcException("HTTP error: " + response.code() + " " + response.message());
                }
                
                if (response.body() == null) {
                    throw new JsonRpcException("Empty response body");
                }
                
                String responseBody = response.body().string();
                return objectMapper.readValue(responseBody, 
                    new TypeReference<List<JsonRpcResponse<Object>>>() {});
            }
        } catch (IOException e) {
            throw new JsonRpcException("Network error", e);
        }
    }
    
    // Notification (no response expected)
    public void notify(String method, Object params) throws JsonRpcException {
        try {
            JsonRpcRequest request = new JsonRpcRequest(method, params, null); // null id for notification
            
            String requestJson = objectMapper.writeValueAsString(request);
            RequestBody body = RequestBody.create(requestJson, JSON);
            
            Request httpRequest = new Request.Builder()
                    .url(baseUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw new JsonRpcException("HTTP error: " + response.code() + " " + response.message());
                }
            }
        } catch (IOException e) {
            throw new JsonRpcException("Network error", e);
        }
    }
    
    // Helper method for easy conversion of LinkedHashMap results
    public <T> T convertResult(Object result, Class<T> targetClass) {
        if (result == null) {
            return null;
        }
        if (targetClass.isInstance(result)) {
            return targetClass.cast(result);
        }
        return objectMapper.convertValue(result, targetClass);
    }
    
    public <T> T convertResult(Object result, TypeReference<T> typeReference) {
        if (result == null) {
            return null;
        }
        return objectMapper.convertValue(result, typeReference);
    }
    
    // Builder pattern for client configuration
    public static class Builder {
        private String baseUrl;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(5);
        private Duration writeTimeout = Duration.ofSeconds(5);
        private Authenticator authenticator;
        
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }
        
        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }
        
        public Builder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }
        
        public Builder writeTimeout(Duration timeout) {
            this.writeTimeout = timeout;
            return this;
        }
        
        public Builder authenticator(Authenticator authenticator) {
            this.authenticator = authenticator;
            return this;
        }
        
        public JsonRpcTransport build() {
            if (baseUrl == null) {
                throw new IllegalArgumentException("baseUrl is required");
            }
            
            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                    .connectTimeout(connectTimeout)
                    .readTimeout(readTimeout)
                    .writeTimeout(writeTimeout);
            
            if (authenticator != null) {
                clientBuilder.authenticator(authenticator);
            }
            
            return new JsonRpcTransport(baseUrl, clientBuilder.build());
        }
    }
    
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    @Override
    public CompletableFuture<SendBlockEnvResponse> sendBlockEnv(SendBlockEnvRequest blockEnv) {
        return this.callAsync(
            CredibleLayerMethods.SEND_BLOCK_ENV, 
            blockEnv,
            SendBlockEnvResponse.class
        );
    }

    @Override
    public CompletableFuture<SendTransactionsResponse> sendTransactions(SendTransactionsRequest transactions) {
        return this.callAsync(
          CredibleLayerMethods.SEND_TRANSACTIONS, 
          transactions, 
          SendTransactionsResponse.class
        );
    }

    @Override
    public CompletableFuture<GetTransactionsResponse> getTransactions(List<String> txHashes) {
        return this.callAsync(
            CredibleLayerMethods.GET_TRANSACTIONS,
            txHashes,
            GetTransactionsResponse.class
        );
    }

    @Override
    public CompletableFuture<ReorgResponse> sendReorg(ReorgRequest reorgRequest) {
        return this.callAsync(
            CredibleLayerMethods.SEND_REORG,
            reorgRequest,
            ReorgResponse.class
        );
    }
}