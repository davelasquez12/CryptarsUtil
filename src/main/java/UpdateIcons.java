import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeAction;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.AttributeValueUpdate;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpdateIcons {
    public static final String TABLE_NAME = "TradingPairs";
    private static DynamoDbClient dynamoDbClient;

    private static void initDynamoDbClient() {
        if(dynamoDbClient == null) {
            dynamoDbClient = DynamoDbClient.builder()
                    .region(Region.US_EAST_2)
                    .credentialsProvider(ProfileCredentialsProvider.create())
                    .build();
        }
    }

    public static void updateAllSupportedIcons() {
        initDynamoDbClient();
        Map<String,String> iconUrlMap = GetIconsService.executeService();

        //Get full list of supported assets from CurrentCryptoPrices table in DynamoDB
        List<Map<String, AttributeValue>> assetList = getAssetListFromDynamoDb();

        //Compare assetList with the iconUrlsMap to record the supported asset icons to DynamoDB
        if(assetList != null && !assetList.isEmpty()) {
            updateDbForMatchingAssets(iconUrlMap, assetList);
        }
    }

    private static List<Map<String, AttributeValue>> getAssetListFromDynamoDb() {
        ScanRequest scanRequest = ScanRequest.builder().tableName(TABLE_NAME).attributesToGet("Base").build();
        ScanResponse response = dynamoDbClient.scan(scanRequest);

        if(response.sdkHttpResponse().isSuccessful() && response.count() > 0) {
            return response.items();
        }

        return null;
    }

    private static void updateDbForMatchingAssets(Map<String, String> iconUrlMap, List<Map<String, AttributeValue>> dbAssetList) {
        UpdateItemRequest updateItemRequest = UpdateItemRequest.builder().tableName(TABLE_NAME).build();

        for(Map<String, AttributeValue> asset : dbAssetList) {
            String assetName = asset.get("Base").s().toUpperCase();
            String iconUrl = iconUrlMap.get(assetName);

            if(iconUrl != null && !AssetExclusionList.contains(assetName)) {
                //store recorded urls to the CurrentCryptoPrices table in a new column "BaseIconUrl"
                updateItemToDb(assetName, iconUrl, updateItemRequest);
            }
        }
    }

    private static void updateItemToDb(String assetName, String url, UpdateItemRequest updateItemRequest) {
        HashMap<String, AttributeValue> primaryKeyItem = new HashMap<>(2);
        primaryKeyItem.put("Base", AttributeValue.builder().s(assetName).build());

        HashMap<String, AttributeValueUpdate> iconUrlUpdate = new HashMap<>(2);
        AttributeValue attributeValue = AttributeValue.builder().s(url).build();
        iconUrlUpdate.put("BaseIconUrl", AttributeValueUpdate.builder().value(attributeValue).action(AttributeAction.PUT).build());

        updateItemRequest = updateItemRequest
                .toBuilder()
                .key(primaryKeyItem)
                .attributeUpdates(iconUrlUpdate)
                .build();

        dynamoDbClient.updateItem(updateItemRequest);
    }

    public static void updateIconUrlByAssetName(String assetName, String iconUrl) {

    }
}
