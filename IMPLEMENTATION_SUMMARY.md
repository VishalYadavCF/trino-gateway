# Trino Gateway Error Handling Implementation Summary

## Problem Statement

When the Trino Gateway queries an external service for routing decisions, the external service may return an error list. However, the Gateway does not propagate these errors to the client. Instead, it logs the error and attempts to route the query to the default "adhoc" group, eventually returning a generic "Number of active backends found zero" error if no valid backend exists.

## Solution Overview

The implementation adds proper error handling and propagation for external routing service errors, with configurable fallback behavior.

## Key Changes

### 1. New Exception Class
**File**: `RoutingException.java`
- Custom exception for routing-related errors
- Carries error messages from external services
- Extends RuntimeException for easy propagation

### 2. Enhanced Response Objects
**File**: `RoutingSelectorResponse.java`
- Added `errors` field to capture external service errors
- Added `hasErrors()` method for easy error checking
- Maintains backward compatibility

**File**: `RoutingGroupResponse.java` (interface)
- Added `errors()` method to interface contract

### 3. Modified External Routing Selector
**File**: `ExternalRoutingGroupSelector.java`
- Changed from throwing exceptions to returning errors in response
- Logs errors for debugging while preserving them for propagation
- Maintains existing behavior for non-error cases

### 4. Enhanced Routing Target Handler
**File**: `RoutingTargetHandler.java`
- Added error checking logic in `getBackendFromRoutingGroup()`
- Configurable behavior: propagate errors or fall back to adhoc
- Throws `RoutingException` with detailed error messages when configured

### 5. Configuration Enhancement
**File**: `RoutingConfiguration.java`
- Added `fallbackToAdhocOnExternalErrors` boolean flag
- Default: `false` (propagate errors for better visibility)
- When `true`: maintains existing fallback behavior

### 6. Updated Tests
**Files**: Test files updated to verify new behavior
- Added error propagation test scenarios
- Updated existing tests to work with new response structure
- Created comprehensive test for error handling logic

## Configuration

```yaml
routing:
  fallbackToAdhocOnExternalErrors: false  # Default: propagate errors
```

## Behavior Matrix

| External Service Response | fallbackToAdhocOnExternalErrors | Result |
|---------------------------|--------------------------------|---------|
| Valid routing group | false/true | Route to specified group |
| Errors present | false | Throw RoutingException with error details |
| Errors present | true | Fall back to adhoc cluster |
| Service unreachable | false/true | Fall back to header-based routing |

## Benefits

1. **Better Error Visibility**: Clients receive meaningful error messages from external routing services
2. **Improved Debugging**: Specific error details help operators troubleshoot routing issues
3. **Configurable Behavior**: Operators can choose between error propagation and high availability
4. **Backward Compatibility**: Existing behavior preserved when fallback is enabled
5. **Flexible Architecture**: Supports custom Trino cluster architectures without assuming "adhoc" exists

## Migration Path

1. **Default Behavior**: No configuration change needed - errors will be propagated (new behavior)
2. **Preserve Existing Behavior**: Set `fallbackToAdhocOnExternalErrors: true` in configuration
3. **Gradual Migration**: Start with fallback enabled, then disable to get better error visibility

## Example Error Response

Before (generic error):
```
Number of active backends found zero
```

After (specific error):
```
External routing service errors: User not authorized for production cluster, Query exceeds resource limits
```

This implementation provides a robust solution for handling external routing service errors while maintaining flexibility for different deployment scenarios.