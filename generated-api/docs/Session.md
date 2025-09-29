
# Session

## Properties
Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **kotlin.String** | Unique identifier for the session |  [optional]
**name** | **kotlin.String** | Session name |  [optional]
**description** | **kotlin.String** | Session description |  [optional]
**status** | [**inline**](#Status) | Current status of the session |  [optional]
**repositoryUrl** | **kotlin.String** | Git repository URL |  [optional]
**repositoryRef** | **kotlin.String** | Git repository reference (branch, tag, or commit) |  [optional]
**createdAt** | [**java.time.OffsetDateTime**](java.time.OffsetDateTime.md) | Session creation timestamp |  [optional]
**updatedAt** | [**java.time.OffsetDateTime**](java.time.OffsetDateTime.md) | Session last update timestamp |  [optional]
**tags** | **kotlin.collections.List&lt;kotlin.String&gt;** | Tags associated with the session |  [optional]


<a id="Status"></a>
## Enum: status
Name | Value
---- | -----
status | PENDING, RUNNING, COMPLETED, FAILED



