# Test Plan for Trino Gateway Error Handling Changes

## Summary of Changes

1. **Created RoutingException**: A new exception class to carry external service errors
2. **Enhanced RoutingSelectorResponse**: Added `errors` field and `hasErrors()` method
3. **Updated RoutingGroupResponse interface**: Added `errors()` method
4. **Modified ExternalRoutingGroupSelector**: Returns errors instead of throwing exceptions
5. **Enhanced RoutingTargetHandler**: Handles errors based on configuration
6. **Added RoutingConfiguration**: New `fallbackToAdhocOnExternalErrors` setting

## Test Scenarios

### Scenario 1: External Service Returns Errors (Fallback Disabled)
- **Configuration**: `fallbackToAdhocOnExternalErrors = false`
- **External Service Response**: `{"errors": ["Service unavailable", "Invalid request"]}`
- **Expected Behavior**: RoutingException thrown with error details
- **Client Receives**: Meaningful error message from external service

### Scenario 2: External Service Returns Errors (Fallback Enabled)
- **Configuration**: `fallbackToAdhocOnExternalErrors = true`
- **External Service Response**: `{"errors": ["Service unavailable"]}`
- **Expected Behavior**: Falls back to "adhoc" routing group
- **Client Receives**: Request routed to adhoc cluster

### Scenario 3: External Service Returns Valid Routing Group
- **External Service Response**: `{"routingGroup": "production", "errors": []}`
- **Expected Behavior**: Normal routing to "production" group
- **Client Receives**: Request routed to production cluster

### Scenario 4: External Service Unreachable
- **External Service**: Connection timeout/error
- **Expected Behavior**: Falls back to X-Trino-Routing-Group header
- **Client Receives**: Request routed based on header or adhoc

## Configuration Example

```yaml
routing:
  fallbackToAdhocOnExternalErrors: false  # Default: false (propagate errors)
```

## Benefits

1. **Better Error Visibility**: Clients receive specific error messages from external routing services
2. **Configurable Fallback**: Operators can choose between error propagation and fallback behavior
3. **Backward Compatibility**: Default behavior preserves existing fallback to header-based routing
4. **Improved Debugging**: Clear error messages help with troubleshooting routing issues

## Files Modified

- `RoutingException.java` (new)
- `RoutingSelectorResponse.java` (enhanced)
- `RoutingGroupResponse.java` (enhanced)
- `ExternalRoutingGroupSelector.java` (modified)
- `RoutingTargetHandler.java` (modified)
- `RoutingConfiguration.java` (enhanced)
- Test files updated to verify new behavior