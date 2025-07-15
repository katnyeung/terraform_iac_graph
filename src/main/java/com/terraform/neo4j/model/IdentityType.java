package com.terraform.neo4j.model;

/**
 * Enum representing the type of identity for infrastructure components.
 * Used to classify resources as identity-related or regular infrastructure resources.
 */
public enum IdentityType {
    /**
     * Resources that represent identity providers, service accounts, IAM roles, etc.
     * Examples: google_service_account, aws_iam_role, azurerm_user_assigned_identity
     */
    IDENTITY_RESOURCE,
    
    /**
     * Regular infrastructure resources like compute instances, storage, networking, etc.
     * Examples: google_compute_instance, aws_ec2_instance, azurerm_virtual_machine
     */
    REGULAR_RESOURCE
}