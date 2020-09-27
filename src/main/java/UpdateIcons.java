import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeAction;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.AttributeValueUpdate;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.util.ArrayList;
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
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
        }
    }

    public static void updateAllSupportedIcons() {
        List<ErrorInfo> errorInfoList = new ArrayList<>();
        initDynamoDbClient();
        Map<String,String> iconUrlMap = GetIconsService.executeService();

        //Get full list of supported assets from CurrentCryptoPrices table in DynamoDB
        List<Map<String, AttributeValue>> assetList = getAssetListFromDynamoDb();

        //Compare assetList with the iconUrlsMap to record the supported asset icons to DynamoDB
        if(assetList != null && !assetList.isEmpty()) {
            updateDbForMatchingAssets(iconUrlMap, assetList, errorInfoList);
        } else {
            errorInfoList.add(new ErrorInfo("Error calling UpdateIcons.getAssetListFromDynamoDb()"));
        }

        if(errorInfoList.isEmpty()) {
            System.out.println("Asset Icon Update completed successfully!");
        } else {
            displayErrors(errorInfoList);
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

    private static void updateDbForMatchingAssets(Map<String, String> iconUrlMap, List<Map<String, AttributeValue>> dbAssetList, List<ErrorInfo> errorInfoList) {
        UpdateItemRequest updateItemRequest = UpdateItemRequest.builder().tableName(TABLE_NAME).build();

        for(Map<String, AttributeValue> asset : dbAssetList) {
            String assetName = asset.get("Base").s().toUpperCase();
            String iconUrl = iconUrlMap.get(assetName);

            if(iconUrl != null && !AssetExclusionList.contains(assetName)) {
                //store recorded urls to the CurrentCryptoPrices table in a new column "BaseIconUrl"
                updateItemToDb(assetName, iconUrl, updateItemRequest, errorInfoList);
            }
        }
    }

    private static void updateItemToDb(String assetName, String url, UpdateItemRequest updateItemRequest, List<ErrorInfo> errorInfoList) {
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

        UpdateItemResponse response = dynamoDbClient.updateItem(updateItemRequest);
        if(!response.sdkHttpResponse().isSuccessful()) {
            errorInfoList.add(new ErrorInfo("Error calling UpdateIcons.updateItemToDb() for assetName: " + assetName + " and url: " + url));
        }
    }

    public static void updateIconUrlByAssetName(String assetName, String iconUrl) {

    }

    private static void displayErrors(List<ErrorInfo> errorInfoList){
        System.out.println(errorInfoList.size() + " errors found: ");
        for(ErrorInfo info : errorInfoList) {
            System.out.println(info.getMessage());
        }
    }
}
