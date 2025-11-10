package ru.selsup.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String authToken;

    private final int requestLimit;
    private final long timeWindowMillis;
    private long tokens;
    private long lastRefillTime;
    private final ReentrantLock bucketLock;

    public CrptApi(TimeUnit timeUnit, int requestLimit, String authToken) {
        this(timeUnit, requestLimit, authToken, "https://ismp.crpt.ru/api/v3/lk/documents/create");
    }

    public CrptApi(TimeUnit timeUnit, int requestLimit, String authToken, String apiUrl) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("Request limit must be positive");
        }

        this.requestLimit = requestLimit;
        this.timeWindowMillis = timeUnit.toMillis(1);
        this.tokens = requestLimit;
        this.lastRefillTime = System.currentTimeMillis();
        this.bucketLock = new ReentrantLock();
        this.authToken = authToken;
        this.apiUrl = apiUrl;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        this.objectMapper = new ObjectMapper();
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public void createDocument(Document document, String signature) throws InterruptedException, IOException {
        acquireToken();
        String requestBody = prepareRequestBody(document, signature);
        sendRequest(requestBody);
    }

    private void acquireToken() throws InterruptedException {
        bucketLock.lock();
        try {
            while (tokens <= 0) {
                refillTokens();
                if (tokens <= 0) {
                    long waitTime = calculateWaitTime();
                    if (waitTime > 0) {
                        Thread.sleep(waitTime);
                    }
                    refillTokens();
                }
            }
            tokens--;
        } finally {
            bucketLock.unlock();
        }
    }

    private void refillTokens() {
        long currentTime = System.currentTimeMillis();
        long timePassed = currentTime - lastRefillTime;

        if (timePassed >= timeWindowMillis) {
            tokens = requestLimit;
            lastRefillTime = currentTime;
        }
    }

    private long calculateWaitTime() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRefill = currentTime - lastRefillTime;
        return Math.max(0, timeWindowMillis - timeSinceLastRefill);
    }

    private String prepareRequestBody(Document document, String signature) throws JsonProcessingException {
        String documentJson = objectMapper.writeValueAsString(document);
        String productDocumentBase64 = Base64.getEncoder().encodeToString(documentJson.getBytes());

        CreateDocumentRequest request = new CreateDocumentRequest(
                "MANUAL",
                productDocumentBase64,
                "LP_INTRODUCE_GOODS",
                signature
        );

        return objectMapper.writeValueAsString(request);
    }

    private void sendRequest(String requestBody) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("API request failed with status: " + response.statusCode() + ", body: " + response.body());
        }
    }


    public static class Document {
        private Description description;
        private String docId;
        private String docStatus;
        private String docType;
        private Boolean importRequest;
        private String ownerInn;
        private String participantInn;
        private String producerInn;
        private String productionDate;
        private String productionType;
        private Product[] products;
        private String regDate;
        private String regNumber;

        // Конструкторы, геттеры и сеттеры
        public Document() {
        }

        public Description getDescription() {
            return description;
        }

        public void setDescription(Description description) {
            this.description = description;
        }

        public String getDocId() {
            return docId;
        }

        public void setDocId(String docId) {
            this.docId = docId;
        }

        public String getDocStatus() {
            return docStatus;
        }

        public void setDocStatus(String docStatus) {
            this.docStatus = docStatus;
        }

        public String getDocType() {
            return docType;
        }

        public void setDocType(String docType) {
            this.docType = docType;
        }

        public Boolean getImportRequest() {
            return importRequest;
        }

        public void setImportRequest(Boolean importRequest) {
            this.importRequest = importRequest;
        }

        public String getOwnerInn() {
            return ownerInn;
        }

        public void setOwnerInn(String ownerInn) {
            this.ownerInn = ownerInn;
        }

        public String getParticipantInn() {
            return participantInn;
        }

        public void setParticipantInn(String participantInn) {
            this.participantInn = participantInn;
        }

        public String getProducerInn() {
            return producerInn;
        }

        public void setProducerInn(String producerInn) {
            this.producerInn = producerInn;
        }

        public String getProductionDate() {
            return productionDate;
        }

        public void setProductionDate(String productionDate) {
            this.productionDate = productionDate;
        }

        public String getProductionType() {
            return productionType;
        }

        public void setProductionType(String productionType) {
            this.productionType = productionType;
        }

        public Product[] getProducts() {
            return products;
        }

        public void setProducts(Product[] products) {
            this.products = products;
        }

        public String getRegDate() {
            return regDate;
        }

        public void setRegDate(String regDate) {
            this.regDate = regDate;
        }

        public String getRegNumber() {
            return regNumber;
        }

        public void setRegNumber(String regNumber) {
            this.regNumber = regNumber;
        }
    }

    public static class Description {
        private String participantInn;

        public Description() {
        }

        public Description(String participantInn) {
            this.participantInn = participantInn;
        }

        public String getParticipantInn() {
            return participantInn;
        }

        public void setParticipantInn(String participantInn) {
            this.participantInn = participantInn;
        }
    }

    public static class Product {
        private String certificateDocument;
        private String certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;

        public Product() {
        }

        public String getCertificateDocument() {
            return certificateDocument;
        }

        public void setCertificateDocument(String certificateDocument) {
            this.certificateDocument = certificateDocument;
        }

        public String getCertificateDocumentDate() {
            return certificateDocumentDate;
        }

        public void setCertificateDocumentDate(String certificateDocumentDate) {
            this.certificateDocumentDate = certificateDocumentDate;
        }

        public String getCertificateDocumentNumber() {
            return certificateDocumentNumber;
        }

        public void setCertificateDocumentNumber(String certificateDocumentNumber) {
            this.certificateDocumentNumber = certificateDocumentNumber;
        }

        public String getOwnerInn() {
            return ownerInn;
        }

        public void setOwnerInn(String ownerInn) {
            this.ownerInn = ownerInn;
        }

        public String getProducerInn() {
            return producerInn;
        }

        public void setProducerInn(String producerInn) {
            this.producerInn = producerInn;
        }

        public String getProductionDate() {
            return productionDate;
        }

        public void setProductionDate(String productionDate) {
            this.productionDate = productionDate;
        }

        public String getTnvedCode() {
            return tnvedCode;
        }

        public void setTnvedCode(String tnvedCode) {
            this.tnvedCode = tnvedCode;
        }

        public String getUitCode() {
            return uitCode;
        }

        public void setUitCode(String uitCode) {
            this.uitCode = uitCode;
        }

        public String getUituCode() {
            return uituCode;
        }

        public void setUituCode(String uituCode) {
            this.uituCode = uituCode;
        }
    }

    private static class CreateDocumentRequest {
        private final String documentFormat;
        private final String productDocument;
        private final String type;
        private final String signature;

        public CreateDocumentRequest(String documentFormat, String productDocument, String type, String signature) {
            this.documentFormat = documentFormat;
            this.productDocument = productDocument;
            this.type = type;
            this.signature = signature;
        }

        public String getDocumentFormat() {
            return documentFormat;
        }

        public String getProductDocument() {
            return productDocument;
        }

        public String getType() {
            return type;
        }

        public String getSignature() {
            return signature;
        }
    }
}
