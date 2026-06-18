import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestJackson {
    public static void main(String[] args) throws Exception {
        String message = "{\"id\":\"8d72aa5e\",\"source\":\"payment-service\",\"type\":\"PaymentSuccess\",\"time\":\"2026-06-14T14:27:11Z\",\"datacontenttype\":\"application/json\",\"data\":{\"order_id\":\"10451eb4-f8c7-4220-bdb0-36299f0fc4a1\",\"payment_id\":\"7daa4ef0-ebbf-4708-b67f-ed2a8e677eca\",\"amount\":142500.0,\"transaction_id\":\"mock_tx_5c79135c5f16420d\"}}";
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode envelope = objectMapper.readTree(message);
        JsonNode data = envelope.path("data");
        System.out.println("Data: " + data);
        System.out.println("orderIdStr: '" + data.path("order_id").asText() + "'");
        System.out.println("isBlank: " + data.path("order_id").asText().isBlank());
    }
}
