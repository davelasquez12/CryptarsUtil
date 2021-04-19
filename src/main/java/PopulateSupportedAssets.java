import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PopulateSupportedAssets {
    public static void run() {
        ScanRequest scanRequest = ScanRequest.builder().tableName("TradingPairs").attributesToGet("id").build();
        ScanResponse scanResponse = Main.getDynamoDbClient().scan(scanRequest);

        if(scanResponse.sdkHttpResponse().isSuccessful() && scanResponse.hasItems()) {
            Set<String> assetSet = new HashSet<>();

            for (Map<String, AttributeValue> item : scanResponse.items()) {
                String assetId = item.get("id").s();
                assetSet.add(assetId);
            }

            saveCryptoAssetIdsToDB(assetSet);
        }
    }

    private static void saveCryptoAssetIdsToDB(Set<String> assetSet) {
        if(assetSet.isEmpty()) {
            System.out.println("Nothing to save to CryptarsSupportedAssets table, assetSet is empty, ");
            return;
        }

        Map<String, AttributeValue> assetIdsItem = new HashMap<>();
        assetIdsItem.put("asset_type", AttributeValue.builder().s("crypto").build());
        assetIdsItem.put("id_set", AttributeValue.builder().ss(assetSet).build());

        PutItemRequest putItemRequest = PutItemRequest.builder().tableName("CryptarsSupportedAssets").item(assetIdsItem).build();
        PutItemResponse putItemResponse = Main.getDynamoDbClient().putItem(putItemRequest);

        if(putItemResponse.sdkHttpResponse().isSuccessful()) {
            System.out.println("Successfully populated CryptarsSupportedAssets for the crypto asset type!");
        }else {
            System.out.println("Failed to populate CryptarsSupportedAssets!");
        }
    }
}
