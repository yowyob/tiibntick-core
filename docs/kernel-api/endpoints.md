# Kernel API — Endpoints Reference

Auto-generated from `IWM Backend API` v0.1.0-SNAPSHOT OpenAPI spec (`openapi.json` in this directory). Server: https://kernel-core.yowyob.com.

**Do not edit by hand** — regenerate with `scripts/fetch-kernel-openapi.sh` (fetches spec) then `python3 scripts/gen_kernel_api_docs.py` (rebuilds this file + `schemas.md`).

1526 operations across 1160 paths, 155 tags, 1078 schemas.

## Tags (controllers)

- [CRM Actions](#crm-actions) (5 ops)
- [Tier Bulk Export](#tier-bulk-export) (1 ops)
- [Tier Bulk Import](#tier-bulk-import) (2 ops)
- [Tier Documents](#tier-documents) (3 ops)
- [Tier Portal](#tier-portal) (5 ops)
- [account-connector-controller](#account-connector-controller) (4 ops)
- [account-type-controller](#account-type-controller) (7 ops)
- [accounting-bookkeeping-controller](#accounting-bookkeeping-controller) (49 ops)
- [accounting-controller](#accounting-controller) (8 ops)
- [accounting-legacy-bookkeeping-controller](#accounting-legacy-bookkeeping-controller) (151 ops)
- [accounting-legacy-brouillard-controller](#accounting-legacy-brouillard-controller) (6 ops)
- [accounting-legacy-operations-controller](#accounting-legacy-operations-controller) (92 ops)
- [accounting-legacy-rapport-controller](#accounting-legacy-rapport-controller) (18 ops)
- [accounting-operations-controller](#accounting-operations-controller) (26 ops)
- [accounting-period-controller](#accounting-period-controller) (3 ops)
- [accounting-read-controller](#accounting-read-controller) (2 ops)
- [accounting-reference-controller](#accounting-reference-controller) (2 ops)
- [accounting-report-controller](#accounting-report-controller) (6 ops)
- [accounting-workflow-controller](#accounting-workflow-controller) (1 ops)
- [actor-address-book-controller](#actor-address-book-controller) (6 ops)
- [actor-controller](#actor-controller) (6 ops)
- [actor-financial-profile-controller](#actor-financial-profile-controller) (1 ops)
- [address-controller](#address-controller) (3 ops)
- [administration-controller](#administration-controller) (25 ops)
- [advanced-asset-management-controller](#advanced-asset-management-controller) (5 ops)
- [agency-controller](#agency-controller) (6 ops)
- [agency-schedule-controller](#agency-schedule-controller) (5 ops)
- [artist-profile-controller](#artist-profile-controller) (4 ops)
- [artwork-controller](#artwork-controller) (19 ops)
- [asset-portfolio-controller](#asset-portfolio-controller) (3 ops)
- [audit-log-controller](#audit-log-controller) (2 ops)
- [auth-controller](#auth-controller) (24 ops)
- [auth-oidc-controller](#auth-oidc-controller) (6 ops)
- [bank-account-check-controller](#bank-account-check-controller) (4 ops)
- [bank-category-controller](#bank-category-controller) (8 ops)
- [bank-controller](#bank-controller) (8 ops)
- [bank-statement-controller](#bank-statement-controller) (16 ops)
- [bank-transaction-controller](#bank-transaction-controller) (7 ops)
- [billing-legacy-documents-controller](#billing-legacy-documents-controller) (64 ops)
- [billing-legacy-payments-controller](#billing-legacy-payments-controller) (7 ops)
- [blockchain-controller](#blockchain-controller) (11 ops)
- [budget-controller](#budget-controller) (7 ops)
- [business-domain-controller](#business-domain-controller) (2 ops)
- [cashier-operations-controller](#cashier-operations-controller) (80 ops)
- [chart-of-accounts-controller](#chart-of-accounts-controller) (6 ops)
- [check-deposit-controller](#check-deposit-controller) (8 ops)
- [check-payment-controller](#check-payment-controller) (8 ops)
- [checkbook-controller](#checkbook-controller) (5 ops)
- [client-application-controller](#client-application-controller) (10 ops)
- [client-controller](#client-controller) (24 ops)
- [contact-controller](#contact-controller) (3 ops)
- [currency-controller](#currency-controller) (3 ops)
- [customer-controller](#customer-controller) (27 ops)
- [declaration-controller](#declaration-controller) (2 ops)
- [department-controller](#department-controller) (7 ops)
- [document-governance-controller](#document-governance-controller) (5 ops)
- [document-hub-controller](#document-hub-controller) (4 ops)
- [email-verification-page-controller](#email-verification-page-controller) (1 ops)
- [employee-controller](#employee-controller) (31 ops)
- [employee-self-service-controller](#employee-self-service-controller) (4 ops)
- [exchange-rate-controller](#exchange-rate-controller) (1 ops)
- [expense-controller](#expense-controller) (9 ops)
- [file-controller](#file-controller) (5 ops)
- [final-settlement-controller](#final-settlement-controller) (4 ops)
- [fiscal-year-controller](#fiscal-year-controller) (4 ops)
- [fixed-asset-controller](#fixed-asset-controller) (7 ops)
- [gallery-event-controller](#gallery-event-controller) (6 ops)
- [garnishment-controller](#garnishment-controller) (6 ops)
- [general-options-controller](#general-options-controller) (16 ops)
- [generalized-inventory-campaign-controller](#generalized-inventory-campaign-controller) (5 ops)
- [inventory-controller](#inventory-controller) (5 ops)
- [inventory-session-controller](#inventory-session-controller) (6 ops)
- [jwk-set-controller](#jwk-set-controller) (1 ops)
- [kyc-verification-controller](#kyc-verification-controller) (1 ops)
- [leave-controller](#leave-controller) (9 ops)
- [ledger-controller](#ledger-controller) (3 ops)
- [legacy-banking-catalog-controller](#legacy-banking-catalog-controller) (28 ops)
- [legacy-banking-controller](#legacy-banking-controller) (5 ops)
- [legacy-check-controller](#legacy-check-controller) (3 ops)
- [legacy-point-of-interest-controller](#legacy-point-of-interest-controller) (4 ops)
- [legacy-reconciliation-controller](#legacy-reconciliation-controller) (2 ops)
- [loan-advance-controller](#loan-advance-controller) (10 ops)
- [lookup-table-admin-controller](#lookup-table-admin-controller) (5 ops)
- [material-request-controller](#material-request-controller) (7 ops)
- [medical-controller](#medical-controller) (8 ops)
- [medical-self-service-controller](#medical-self-service-controller) (1 ops)
- [mission-order-controller](#mission-order-controller) (12 ops)
- [notification-controller](#notification-controller) (10 ops)
- [observability-controller](#observability-controller) (5 ops)
- [opening-hours-controller](#opening-hours-controller) (2 ops)
- [operation-template-controller](#operation-template-controller) (4 ops)
- [operational-excellence-controller](#operational-excellence-controller) (9 ops)
- [operational-policy-controller](#operational-policy-controller) (4 ops)
- [operational-site-governance-controller](#operational-site-governance-controller) (5 ops)
- [operational-workspace-controller](#operational-workspace-controller) (6 ops)
- [organization-address-book-controller](#organization-address-book-controller) (12 ops)
- [organization-controller](#organization-controller) (12 ops)
- [organization-service-controller](#organization-service-controller) (11 ops)
- [organization-structure-controller](#organization-structure-controller) (12 ops)
- [pay-element-admin-controller](#pay-element-admin-controller) (5 ops)
- [pay-variable-controller](#pay-variable-controller) (3 ops)
- [payment-controller](#payment-controller) (7 ops)
- [payroll-document-controller](#payroll-document-controller) (6 ops)
- [payroll-employee-controller](#payroll-employee-controller) (9 ops)
- [payroll-onboarding-controller](#payroll-onboarding-controller) (1 ops)
- [payroll-run-controller](#payroll-run-controller) (11 ops)
- [payslip-self-service-controller](#payslip-self-service-controller) (2 ops)
- [physical-space-controller](#physical-space-controller) (3 ops)
- [plan-controller](#plan-controller) (6 ops)
- [platform-authorization-controller](#platform-authorization-controller) (1 ops)
- [platform-service-controller](#platform-service-controller) (3 ops)
- [point-of-interest-controller](#point-of-interest-controller) (2 ops)
- [product-catalog-controller](#product-catalog-controller) (7 ops)
- [product-controller](#product-controller) (6 ops)
- [product-structure-controller](#product-structure-controller) (15 ops)
- [product-transformation-controller](#product-transformation-controller) (3 ops)
- [prospect-controller](#prospect-controller) (26 ops)
- [public-organization-branding-controller](#public-organization-branding-controller) (1 ops)
- [purchase-order-controller](#purchase-order-controller) (5 ops)
- [receipt-controller](#receipt-controller) (4 ops)
- [reconciliation-match-controller](#reconciliation-match-controller) (12 ops)
- [recruitment-controller](#recruitment-controller) (21 ops)
- [resource-address-book-controller](#resource-address-book-controller) (6 ops)
- [resource-controller](#resource-controller) (17 ops)
- [resource-target-controller](#resource-target-controller) (8 ops)
- [retroactive-controller](#retroactive-controller) (5 ops)
- [review-controller](#review-controller) (10 ops)
- [review-self-service-controller](#review-self-service-controller) (1 ops)
- [rh-kpi-controller](#rh-kpi-controller) (3 ops)
- [role-controller](#role-controller) (7 ops)
- [sales-agent-controller](#sales-agent-controller) (24 ops)
- [sales-controller](#sales-controller) (7 ops)
- [scoped-resource-controller](#scoped-resource-controller) (9 ops)
- [session-tokens-controller](#session-tokens-controller) (2 ops)
- [settings-controller](#settings-controller) (2 ops)
- [skill-controller](#skill-controller) (7 ops)
- [social-declaration-controller](#social-declaration-controller) (6 ops)
- [statement-line-controller](#statement-line-controller) (2 ops)
- [supplier-controller](#supplier-controller) (24 ops)
- [system-audit-controller](#system-audit-controller) (4 ops)
- [tax-bracket-admin-controller](#tax-bracket-admin-controller) (5 ops)
- [tax-controller](#tax-controller) (3 ops)
- [third-party-address-book-controller](#third-party-address-book-controller) (6 ops)
- [third-party-controller](#third-party-controller) (10 ops)
- [timesheet-controller](#timesheet-controller) (7 ops)
- [training-budget-controller](#training-budget-controller) (5 ops)
- [training-controller](#training-controller) (16 ops)
- [transaction-type-controller](#transaction-type-controller) (6 ops)
- [treasury-controller](#treasury-controller) (11 ops)
- [user-controller](#user-controller) (5 ops)
- [warehouse-controller](#warehouse-controller) (4 ops)
- [warehouse-layout-controller](#warehouse-layout-controller) (2 ops)
- [warehouse-location-controller](#warehouse-location-controller) (3 ops)
- [warehouse-transfer-controller](#warehouse-transfer-controller) (3 ops)
- [workflow-request-controller](#workflow-request-controller) (4 ops)

## CRM Actions

### GET `/api/v1/actions`
Lister les actions — par tiers ou par utilisateur assigné

**Parameters:**
- `entityId` (query, string(uuid), optional)
- `entityType` (query, string, optional)
- `assignedTo` (query, string(uuid), optional)
- `status` (query, string, optional)

**Responses:** 200 → `CrmAction[]`


### POST `/api/v1/actions`
Créer une action CRM

**Request body:** `application/json` → `CreateActionRequest` (required)

**Responses:** 201 → `CrmAction`


### DELETE `/api/v1/actions/{id}`
Supprimer une action

**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 204


### GET `/api/v1/actions/{id}`
Détail d'une action

**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `CrmAction`


### PATCH `/api/v1/actions/{id}`
Modifier une action (statut, date, note…)

**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateActionRequest` (required)

**Responses:** 200 → `CrmAction`



## Tier Bulk Export

### GET `/api/v1/tiers/export`
Exporter des tiers en CSV

**Parameters:**
- `organizationId` (query, string(uuid), required)
- `type` (query, string, required) — Type de tiers

**Responses:** 200 → `string`



## Tier Bulk Import

### POST `/api/v1/tiers/import`
Importer des tiers depuis un fichier CSV ou XLSX

**Parameters:**
- `organizationId` (query, string(uuid), optional) — Organisation cible (sinon contexte courant)
- `type` (query, string, required) — Type de tiers à créer

**Request body:** `multipart/form-data` → `object` (optional)

**Responses:** 200 → `TierImportResult`


### GET `/api/v1/tiers/import/template`
Télécharger un modèle CSV vide

**Parameters:**
- `type` (query, string, required) — Type de tiers

**Responses:** 200



## Tier Documents

### GET `/api/v1/tiers/{tierId}/documents`
Lister les documents d'un tiers

**Parameters:**
- `tierId` (path, string(uuid), required)

**Responses:** 200 → `TierDocument[]`


### POST `/api/v1/tiers/{tierId}/documents`
Uploader un document pour un tiers

**Parameters:**
- `tierId` (path, string(uuid), required)
- `X-Tenant-Id` (header, string(uuid), required)
- `X-User-Id` (header, string(uuid), optional)
- `documentType` (query, string, required) — Type de document
- `label` (query, string, optional) — Libellé optionnel

**Request body:** `multipart/form-data` → `object` (optional)

**Responses:** 201 → `TierDocument`


### DELETE `/api/v1/tiers/{tierId}/documents/{documentId}`
Supprimer un document

**Parameters:**
- `tierId` (path, string(uuid), required)
- `documentId` (path, string(uuid), required)

**Responses:** 204



## Tier Portal

### GET `/api/v1/portal/bank-accounts`
Mes comptes bancaires

**Responses:** 200 → `BankAccount[]`


### POST `/api/v1/portal/change-password`
Changer mon mot de passe

**Request body:** `application/json` → `ChangePasswordRequest` (required)

**Responses:** 204


### GET `/api/v1/portal/dashboard`
Tableau de bord enrichi

**Responses:** 200 → `TierPortalStatistics`


### GET `/api/v1/portal/me`
Mon profil tiers

**Responses:** 200 → `ThirdParty`


### PATCH `/api/v1/portal/me`
Mettre à jour mon profil

**Request body:** `application/json` → `ThirdParty` (required)

**Responses:** 200 → `ThirdParty`



## account-connector-controller

### GET `/api/treasury/account-connectors`
**Responses:** 200 → `ApiResponseListAccountConnectorTypeResponse`


### POST `/api/treasury/account-connectors`
**Request body:** `application/json` → `RegisterAccountConnectorTypeRequest` (required)

**Responses:** 200 → `ApiResponseAccountConnectorTypeResponse`


### GET `/api/treasury/account-connectors/{connectorTypeId}/fields`
**Parameters:**
- `connectorTypeId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListConnectorFieldResponse`


### POST `/api/treasury/account-connectors/{connectorTypeId}/fields`
**Parameters:**
- `connectorTypeId` (path, string(uuid), required)

**Request body:** `application/json` → `AddConnectorFieldRequest` (required)

**Responses:** 200 → `ApiResponseConnectorFieldResponse`



## account-type-controller

### GET `/api/treasury/account-types`
**Responses:** 200 → `ApiResponseListAccountTypeResponse`


### POST `/api/treasury/account-types`
**Request body:** `application/json` → `RegisterAccountTypeRequest` (required)

**Responses:** 200 → `ApiResponseAccountTypeResponse`


### GET `/api/treasury/account-types/sub-types`
**Parameters:**
- `accountTypeId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListAccountSubTypeResponse`


### POST `/api/treasury/account-types/sub-types`
**Request body:** `application/json` → `RegisterAccountSubTypeRequest` (required)

**Responses:** 200 → `ApiResponseAccountSubTypeResponse`


### GET `/api/treasury/account-types/{accountTypeId}/sub-types`
**Parameters:**
- `accountTypeId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListAccountSubTypeResponse`


### GET `/api/treasury/account-types/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseAccountTypeResponse`


### POST `/api/treasury/account-types/{id}/deactivate`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseAccountTypeResponse`



## accounting-bookkeeping-controller

### GET `/api/accounting-service/accounts`
**Responses:** 200 → `AccountView[]`


### POST `/api/accounting-service/accounts`
**Request body:** `application/json` → `CreateAccountRequest` (required)

**Responses:** 200 → `AccountView`


### POST `/api/accounting-service/accounts/generate`
**Request body:** `application/json` → `GenerateAccountRequest` (required)

**Responses:** 200 → `AccountView`


### GET `/api/accounting-service/accounts/search`
**Parameters:**
- `query` (query, string, required)

**Responses:** 200 → `AccountView[]`


### GET `/api/accounting-service/accounts/type/{accountType}`
**Parameters:**
- `accountType` (path, string, required)

**Responses:** 200 → `AccountView[]`


### DELETE `/api/accounting-service/accounts/{accountId}`
**Parameters:**
- `accountId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting-service/accounts/{accountId}`
**Parameters:**
- `accountId` (path, string(uuid), required)

**Responses:** 200 → `AccountView`


### PUT `/api/accounting-service/accounts/{accountId}`
**Parameters:**
- `accountId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateAccountRequest` (required)

**Responses:** 200 → `AccountView`


### GET `/api/accounting-service/bank-reconciliations`
**Responses:** 200 → `BankReconciliationView[]`


### POST `/api/accounting-service/bank-reconciliations`
**Request body:** `application/json` → `CreateBankReconciliationRequest` (required)

**Responses:** 200 → `BankReconciliationView`


### GET `/api/accounting-service/bank-statement-postings`
**Responses:** 200 → `BankStatementPostingView[]`


### POST `/api/accounting-service/bank-statement-postings`
**Request body:** `application/json` → `CreateBankStatementPostingRequest` (required)

**Responses:** 200 → `BankStatementPostingView`


### GET `/api/accounting-service/cash-register-postings`
**Responses:** 200 → `CashRegisterPostingView[]`


### POST `/api/accounting-service/cash-register-postings`
**Request body:** `application/json` → `CreateCashRegisterPostingRequest` (required)

**Responses:** 200 → `CashRegisterPostingView`


### GET `/api/accounting-service/draft-entries`
**Responses:** 200 → `DraftEntryView[]`


### POST `/api/accounting-service/draft-entries`
**Request body:** `application/json` → `CreateDraftEntryRequest` (required)

**Responses:** 200 → `DraftEntryView`


### POST `/api/accounting-service/draft-entries/{draftEntryId}/post`
**Parameters:**
- `draftEntryId` (path, string(uuid), required)

**Responses:** 200 → `AccountingEntryView`


### GET `/api/accounting-service/entries`
**Responses:** 200 → `AccountingEntryView[]`


### POST `/api/accounting-service/entries`
**Request body:** `application/json` → `CreateEntryRequest` (required)

**Responses:** 200 → `AccountingEntryView`


### POST `/api/accounting-service/entries/generate`
**Request body:** `application/json` → `CreateOperationRequest` (required)

**Responses:** 200 → `AccountingEntryView`


### GET `/api/accounting-service/entries/non-validated`
**Responses:** 200 → `AccountingEntryView[]`


### GET `/api/accounting-service/entries/search`
**Parameters:**
- `journalId` (query, string(uuid), optional)

**Responses:** 200 → `AccountingEntryView[]`


### DELETE `/api/accounting-service/entries/{entryId}`
**Parameters:**
- `entryId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting-service/entries/{entryId}`
**Parameters:**
- `entryId` (path, string(uuid), required)

**Responses:** 200 → `AccountingEntryView`


### PUT `/api/accounting-service/entries/{entryId}`
**Parameters:**
- `entryId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateEntryRequest` (required)

**Responses:** 200 → `AccountingEntryView`


### PUT `/api/accounting-service/entries/{entryId}/cancel`
**Parameters:**
- `entryId` (path, string(uuid), required)

**Responses:** 200 → `AccountingEntryView`


### PUT `/api/accounting-service/entries/{entryId}/deactivate`
**Parameters:**
- `entryId` (path, string(uuid), required)

**Responses:** 200


### POST `/api/accounting-service/entries/{entryId}/validate`
**Parameters:**
- `entryId` (path, string(uuid), required)

**Responses:** 200 → `AccountingEntryView`


### GET `/api/accounting-service/invoice-accounting`
**Responses:** 200 → `InvoiceAccountingView[]`


### POST `/api/accounting-service/invoice-accounting`
**Request body:** `application/json` → `CreateInvoiceAccountingRequest` (required)

**Responses:** 200 → `InvoiceAccountingView`


### GET `/api/accounting-service/invoice-uploads`
**Responses:** 200 → `InvoiceUploadView[]`


### POST `/api/accounting-service/invoice-uploads`
**Request body:** `application/json` → `CreateInvoiceUploadRequest` (required)

**Responses:** 200 → `InvoiceUploadView`


### GET `/api/accounting-service/journal-audits`
**Responses:** 200 → `JournalAuditView[]`


### GET `/api/accounting-service/journals/count/type/{type}`
**Parameters:**
- `type` (path, string, required)

**Responses:** 200 → `integer(int64)`


### GET `/api/accounting-service/journals/search`
**Parameters:**
- `query` (query, string, required)

**Responses:** 200 → `JournalView[]`


### GET `/api/accounting-service/journals/type/{type}`
**Parameters:**
- `type` (path, string, required)

**Responses:** 200 → `JournalView[]`


### GET `/api/accounting-service/letterings`
**Responses:** 200 → `LetteringView[]`


### POST `/api/accounting-service/letterings`
**Request body:** `application/json` → `CreateLetteringRequest` (required)

**Responses:** 200 → `LetteringView`


### GET `/api/accounting-service/operations`
**Responses:** 200 → `AccountingOperationView[]`


### POST `/api/accounting-service/operations`
**Request body:** `application/json` → `CreateOperationRequest` (required)

**Responses:** 200 → `AccountingOperationView`


### GET `/api/accounting-service/plan-accounts`
**Responses:** 200 → `PlanAccountView[]`


### POST `/api/accounting-service/plan-accounts`
**Request body:** `application/json` → `CreatePlanAccountRequest` (required)

**Responses:** 200 → `PlanAccountView`


### GET `/api/accounting-service/plan-accounts/class/{accountClass}`
**Parameters:**
- `accountClass` (path, string, required)

**Responses:** 200 → `PlanAccountView[]`


### POST `/api/accounting-service/plan-accounts/init-ohada`
**Responses:** 200 → `PlanAccountView[]`


### GET `/api/accounting-service/pointings`
**Responses:** 200 → `PointingView[]`


### POST `/api/accounting-service/pointings`
**Request body:** `application/json` → `CreatePointingRequest` (required)

**Responses:** 200 → `PointingView`


### POST `/api/accounting-service/settings`
**Request body:** `application/json` → `UpsertSettingRequest` (required)

**Responses:** 200 → `AccountingSettingView`


### GET `/api/accounting-service/stock-movement-postings`
**Responses:** 200 → `StockMovementPostingView[]`


### POST `/api/accounting-service/stock-movement-postings`
**Request body:** `application/json` → `CreateStockMovementPostingRequest` (required)

**Responses:** 200 → `StockMovementPostingView`



## accounting-controller

### GET `/api/accounting/invoices`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListInvoiceResponse`


### POST `/api/accounting/invoices`
**Request body:** `application/json` → `CreateInvoiceRequest` (required)

**Responses:** 200 → `ApiResponseInvoiceResponse`


### POST `/api/accounting/invoices/from-orders/{orderId}`
**Parameters:**
- `orderId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseInvoiceResponse`


### DELETE `/api/accounting/invoices/{invoiceId}`
**Parameters:**
- `invoiceId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### GET `/api/accounting/invoices/{invoiceId}`
**Parameters:**
- `invoiceId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseInvoiceResponse`


### PATCH `/api/accounting/invoices/{invoiceId}`
**Parameters:**
- `invoiceId` (path, string(uuid), required)

**Request body:** `application/json` → `CreateInvoiceRequest` (required)

**Responses:** 200 → `ApiResponseInvoiceResponse`


### POST `/api/accounting/invoices/{invoiceId}/post`
**Parameters:**
- `invoiceId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseInvoiceResponse`


### POST `/api/accounting/invoices/{invoiceId}/validate`
**Parameters:**
- `invoiceId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseInvoiceResponse`



## accounting-legacy-bookkeeping-controller

### GET `/api/accounting-service/audit`
**Parameters:**
- `action` (query, string, optional)
- `entryId` (query, string(uuid), optional)
- `query` (query, string, optional)

**Responses:** 200 → `JournalAuditView[]`


### GET `/api/accounting-service/audit/action/{action}`
**Parameters:**
- `action` (path, string, required)

**Responses:** 200 → `JournalAuditView[]`


### GET `/api/accounting-service/audit/entry/{entryId}`
**Parameters:**
- `entryId` (path, string(uuid), required)

**Responses:** 200 → `JournalAuditView[]`


### GET `/api/accounting-service/audit/organization/{organizationId}`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Responses:** 200 → `JournalAuditView[]`


### GET `/api/accounting-service/audit/period/{periodId}`
**Parameters:**
- `periodId` (path, string(uuid), required)

**Responses:** 200 → `JournalAuditView[]`


### GET `/api/accounting-service/audit/search`
**Parameters:**
- `query` (query, string, required)

**Responses:** 200 → `JournalAuditView[]`


### GET `/api/accounting-service/audit/user/{userId}`
**Parameters:**
- `userId` (path, string(uuid), required)

**Responses:** 200 → `JournalAuditView[]`


### GET `/api/accounting-service/bank-statements/{statementId}/candidates`
**Parameters:**
- `statementId` (path, string(uuid), required)

**Responses:** 200 → `AccountingEntryView[]`


### POST `/api/accounting-service/bank-statements/{statementId}/reconcile/{detailId}`
**Parameters:**
- `statementId` (path, string(uuid), required)
- `detailId` (path, string(uuid), required)

**Responses:** 200 → `BankReconciliationView`


### GET `/api/accounting-service/brouillards`
**Responses:** 200 → `DraftEntryView[]`


### POST `/api/accounting-service/brouillards/upload`
**Request body:** `application/json` → `CreateDraftEntryRequest` (required)

**Responses:** 200 → `DraftEntryView`


### DELETE `/api/accounting-service/brouillards/{draftEntryId}`
**Parameters:**
- `draftEntryId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting-service/brouillards/{draftEntryId}`
**Parameters:**
- `draftEntryId` (path, string(uuid), required)

**Responses:** 200 → `DraftEntryView`


### POST `/api/accounting-service/brouillards/{draftEntryId}/reject`
**Parameters:**
- `draftEntryId` (path, string(uuid), required)

**Responses:** 200


### POST `/api/accounting-service/brouillards/{draftEntryId}/validate`
**Parameters:**
- `draftEntryId` (path, string(uuid), required)

**Responses:** 200 → `AccountingEntryView`


### GET `/api/accounting-service/comptes`
**Responses:** 200 → `AccountView[]`


### POST `/api/accounting-service/comptes`
**Request body:** `application/json` → `CreateAccountRequest` (required)

**Responses:** 200 → `AccountView`


### GET `/api/accounting-service/comptes/search`
**Parameters:**
- `query` (query, string, required)

**Responses:** 200 → `AccountView[]`


### GET `/api/accounting-service/comptes/type/{accountType}`
**Parameters:**
- `accountType` (path, string, required)

**Responses:** 200 → `AccountView[]`


### DELETE `/api/accounting-service/comptes/{accountId}`
**Parameters:**
- `accountId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting-service/comptes/{accountId}`
**Parameters:**
- `accountId` (path, string(uuid), required)

**Responses:** 200 → `AccountView`


### PUT `/api/accounting-service/comptes/{accountId}`
**Parameters:**
- `accountId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateAccountRequest` (required)

**Responses:** 200 → `AccountView`


### GET `/api/accounting-service/currencies`
**Responses:** 200 → `CurrencyView[]`


### POST `/api/accounting-service/currencies`
**Request body:** `application/json` → `CreateCurrencyRequest` (required)

**Responses:** 200 → `CurrencyView`


### DELETE `/api/accounting-service/currencies/{currencyId}`
**Parameters:**
- `currencyId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting-service/currencies/{currencyId}`
**Parameters:**
- `currencyId` (path, string(uuid), required)

**Responses:** 200 → `CurrencyView`


### PUT `/api/accounting-service/currencies/{currencyId}`
**Parameters:**
- `currencyId` (path, string(uuid), required)

**Request body:** `application/json` → `CreateCurrencyRequest` (required)

**Responses:** 200 → `CurrencyView`


### GET `/api/accounting-service/ecritures`
**Responses:** 200 → `AccountingEntryView[]`


### POST `/api/accounting-service/ecritures`
**Request body:** `application/json` → `CreateEntryRequest` (required)

**Responses:** 200 → `AccountingEntryView`


### POST `/api/accounting-service/ecritures/generate`
**Request body:** `application/json` → `CreateOperationRequest` (required)

**Responses:** 200 → `AccountingEntryView`


### GET `/api/accounting-service/ecritures/non-validated`
**Responses:** 200 → `AccountingEntryView[]`


### GET `/api/accounting-service/ecritures/search`
**Parameters:**
- `query` (query, string, optional)
- `journalId` (query, string(uuid), optional)

**Responses:** 200 → `AccountingEntryView[]`


### DELETE `/api/accounting-service/ecritures/{entryId}`
**Parameters:**
- `entryId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting-service/ecritures/{entryId}`
**Parameters:**
- `entryId` (path, string(uuid), required)

**Responses:** 200 → `AccountingEntryView`


### PUT `/api/accounting-service/ecritures/{entryId}`
**Parameters:**
- `entryId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateEntryRequest` (required)

**Responses:** 200 → `AccountingEntryView`


### POST `/api/accounting-service/ecritures/{entryId}/validate`
**Parameters:**
- `entryId` (path, string(uuid), required)

**Responses:** 200 → `AccountingEntryView`


### GET `/api/accounting-service/exchange-rates`
**Responses:** 200 → `ExchangeRateView[]`


### POST `/api/accounting-service/exchange-rates`
**Request body:** `application/json` → `CreateExchangeRateRequest` (required)

**Responses:** 200 → `ExchangeRateView`


### GET `/api/accounting-service/exchange-rates/latest`
**Responses:** 200 → `ExchangeRateView`


### DELETE `/api/accounting-service/exchange-rates/{exchangeRateId}`
**Parameters:**
- `exchangeRateId` (path, string(uuid), required)

**Responses:** 200


### POST `/api/accounting-service/invoices/purchase`
**Request body:** `application/json` → `CreateInvoiceAccountingRequest` (required)

**Responses:** 200 → `InvoiceAccountingView`


### POST `/api/accounting-service/invoices/sale`
**Request body:** `application/json` → `CreateInvoiceAccountingRequest` (required)

**Responses:** 200 → `InvoiceAccountingView`


### POST `/api/accounting-service/invoices/upload`
**Request body:** `application/json` → `CreateInvoiceUploadRequest` (required)

**Responses:** 200 → `InvoiceUploadView`


### GET `/api/accounting-service/journals`
**Responses:** 200 → `JournalView[]`


### POST `/api/accounting-service/journals`
**Request body:** `application/json` → `CreateJournalRequest` (required)

**Responses:** 200 → `JournalView`


### GET `/api/accounting-service/journals/active`
**Responses:** 200 → `JournalView[]`


### DELETE `/api/accounting-service/journals/{journalId}`
**Parameters:**
- `journalId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting-service/journals/{journalId}`
**Parameters:**
- `journalId` (path, string(uuid), required)

**Responses:** 200 → `JournalView`


### PUT `/api/accounting-service/journals/{journalId}`
**Parameters:**
- `journalId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateJournalRequest` (required)

**Responses:** 200 → `JournalView`


### GET `/api/accounting-service/journals/{journalId}/comptes`
**Parameters:**
- `journalId` (path, string(uuid), required)

**Responses:** 200 → `AccountView[]`


### GET `/api/accounting-service/operations/by-no-compte`
**Parameters:**
- `accountNo` (query, string, required)

**Responses:** 200 → `AccountingOperationView[]`


### GET `/api/accounting-service/operations/search`
**Parameters:**
- `query` (query, string, required)

**Responses:** 200 → `AccountingOperationView[]`


### DELETE `/api/accounting-service/operations/{operationId}`
**Parameters:**
- `operationId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting-service/operations/{operationId}`
**Parameters:**
- `operationId` (path, string(uuid), required)

**Responses:** 200 → `AccountingOperationView`


### PUT `/api/accounting-service/operations/{operationId}`
**Parameters:**
- `operationId` (path, string(uuid), required)

**Request body:** `application/json` → `CreateOperationRequest` (required)

**Responses:** 200 → `AccountingOperationView`


### GET `/api/accounting-service/plan-comptable`
**Responses:** 200 → `PlanAccountView[]`


### POST `/api/accounting-service/plan-comptable`
**Request body:** `application/json` → `CreatePlanAccountRequest` (required)

**Responses:** 200 → `PlanAccountView`


### GET `/api/accounting-service/plan-comptable/actifs`
**Responses:** 200 → `PlanAccountView[]`


### POST `/api/accounting-service/plan-comptable/admin/init-ohada`
**Responses:** 200 → `PlanAccountView[]`


### GET `/api/accounting-service/plan-comptable/classe/{classe}`
**Parameters:**
- `classe` (path, string, required)

**Responses:** 200 → `PlanAccountView[]`


### POST `/api/accounting-service/plan-comptable/import`
**Request body:** `application/json` → `CreatePlanAccountRequest[]` (required)

**Responses:** 200 → `PlanAccountView[]`


### GET `/api/accounting-service/plan-comptable/prefix/{prefix}`
**Parameters:**
- `prefix` (path, string, required)

**Responses:** 200 → `PlanAccountView[]`


### DELETE `/api/accounting-service/plan-comptable/{planAccountId}`
**Parameters:**
- `planAccountId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting-service/plan-comptable/{planAccountId}`
**Parameters:**
- `planAccountId` (path, string(uuid), required)

**Responses:** 200 → `PlanAccountView`


### PUT `/api/accounting-service/plan-comptable/{planAccountId}`
**Parameters:**
- `planAccountId` (path, string(uuid), required)

**Request body:** `application/json` → `CreatePlanAccountRequest` (required)

**Responses:** 200 → `PlanAccountView`


### POST `/api/accounting-service/pointage/import`
**Request body:** `application/json` → `CreatePointingRequest` (required)

**Responses:** 200 → `PointingView`


### GET `/api/accounting-service/settings`
**Responses:** 200 → `AccountingSettingView[]`


### PUT `/api/accounting-service/settings`
**Request body:** `application/json` → `UpsertSettingRequest` (required)

**Responses:** 200 → `AccountingSettingView`


### GET `/api/accounting-service/settings/{type}`
**Parameters:**
- `type` (path, string, required)

**Responses:** 200 → `AccountingSettingView`


### GET `/api/accounting-service/taxes`
**Responses:** 200 → `TaxDefinitionView[]`


### POST `/api/accounting-service/taxes`
**Request body:** `application/json` → `CreateTaxDefinitionRequest` (required)

**Responses:** 200 → `TaxDefinitionView`


### DELETE `/api/accounting-service/taxes/{taxId}`
**Parameters:**
- `taxId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting-service/taxes/{taxId}`
**Parameters:**
- `taxId` (path, string(uuid), required)

**Responses:** 200 → `TaxDefinitionView`


### PUT `/api/accounting-service/taxes/{taxId}`
**Parameters:**
- `taxId` (path, string(uuid), required)

**Request body:** `application/json` → `CreateTaxDefinitionRequest` (required)

**Responses:** 200 → `TaxDefinitionView`


### GET `/api/accounting/audit`
**Parameters:**
- `action` (query, string, optional)
- `entryId` (query, string(uuid), optional)
- `query` (query, string, optional)

**Responses:** 200 → `JournalAuditView[]`


### GET `/api/accounting/audit/action/{action}`
**Parameters:**
- `action` (path, string, required)

**Responses:** 200 → `JournalAuditView[]`


### GET `/api/accounting/audit/entry/{entryId}`
**Parameters:**
- `entryId` (path, string(uuid), required)

**Responses:** 200 → `JournalAuditView[]`


### GET `/api/accounting/audit/organization/{organizationId}`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Responses:** 200 → `JournalAuditView[]`


### GET `/api/accounting/audit/period/{periodId}`
**Parameters:**
- `periodId` (path, string(uuid), required)

**Responses:** 200 → `JournalAuditView[]`


### GET `/api/accounting/audit/search`
**Parameters:**
- `query` (query, string, required)

**Responses:** 200 → `JournalAuditView[]`


### GET `/api/accounting/audit/user/{userId}`
**Parameters:**
- `userId` (path, string(uuid), required)

**Responses:** 200 → `JournalAuditView[]`


### GET `/api/accounting/bank-statements/{statementId}/candidates`
**Parameters:**
- `statementId` (path, string(uuid), required)

**Responses:** 200 → `AccountingEntryView[]`


### POST `/api/accounting/bank-statements/{statementId}/reconcile/{detailId}`
**Parameters:**
- `statementId` (path, string(uuid), required)
- `detailId` (path, string(uuid), required)

**Responses:** 200 → `BankReconciliationView`


### GET `/api/accounting/comptes`
**Responses:** 200 → `AccountView[]`


### POST `/api/accounting/comptes`
**Request body:** `application/json` → `CreateAccountRequest` (required)

**Responses:** 200 → `AccountView`


### GET `/api/accounting/comptes/search`
**Parameters:**
- `query` (query, string, required)

**Responses:** 200 → `AccountView[]`


### GET `/api/accounting/comptes/type/{accountType}`
**Parameters:**
- `accountType` (path, string, required)

**Responses:** 200 → `AccountView[]`


### DELETE `/api/accounting/comptes/{accountId}`
**Parameters:**
- `accountId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting/comptes/{accountId}`
**Parameters:**
- `accountId` (path, string(uuid), required)

**Responses:** 200 → `AccountView`


### PUT `/api/accounting/comptes/{accountId}`
**Parameters:**
- `accountId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateAccountRequest` (required)

**Responses:** 200 → `AccountView`


### GET `/api/accounting/currencies`
**Parameters:**
- `onlyActive` (query, boolean, optional)

**Responses:** 200


### POST `/api/accounting/currencies`
**Request body:** `application/json` → `CreateCurrencyRequest` (required)

**Responses:** 200


### DELETE `/api/accounting/currencies/{currencyId}`
**Parameters:**
- `currencyId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting/currencies/{currencyId}`
**Parameters:**
- `currencyId` (path, string(uuid), required)

**Responses:** 200 → `CurrencyView`


### PUT `/api/accounting/currencies/{currencyId}`
**Parameters:**
- `currencyId` (path, string(uuid), required)

**Request body:** `application/json` → `CreateCurrencyRequest` (required)

**Responses:** 200 → `CurrencyView`


### GET `/api/accounting/ecritures`
**Responses:** 200 → `AccountingEntryView[]`


### POST `/api/accounting/ecritures`
**Request body:** `application/json` → `CreateEntryRequest` (required)

**Responses:** 200 → `AccountingEntryView`


### POST `/api/accounting/ecritures/generate`
**Request body:** `application/json` → `CreateOperationRequest` (required)

**Responses:** 200 → `AccountingEntryView`


### GET `/api/accounting/ecritures/non-validated`
**Responses:** 200 → `AccountingEntryView[]`


### GET `/api/accounting/ecritures/search`
**Parameters:**
- `query` (query, string, optional)
- `journalId` (query, string(uuid), optional)

**Responses:** 200 → `AccountingEntryView[]`


### DELETE `/api/accounting/ecritures/{entryId}`
**Parameters:**
- `entryId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting/ecritures/{entryId}`
**Parameters:**
- `entryId` (path, string(uuid), required)

**Responses:** 200 → `AccountingEntryView`


### PUT `/api/accounting/ecritures/{entryId}`
**Parameters:**
- `entryId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateEntryRequest` (required)

**Responses:** 200 → `AccountingEntryView`


### POST `/api/accounting/ecritures/{entryId}/validate`
**Parameters:**
- `entryId` (path, string(uuid), required)

**Responses:** 200 → `AccountingEntryView`


### GET `/api/accounting/exchange-rates`
**Responses:** 200


### POST `/api/accounting/exchange-rates`
**Request body:** `application/json` → `CreateExchangeRateRequest` (required)

**Responses:** 200


### GET `/api/accounting/exchange-rates/latest`
**Parameters:**
- `sourceId` (query, string(uuid), required)
- `targetId` (query, string(uuid), required)
- `date` (query, string(date-time), optional)

**Responses:** 200


### DELETE `/api/accounting/exchange-rates/{exchangeRateId}`
**Parameters:**
- `exchangeRateId` (path, string(uuid), required)

**Responses:** 200


### POST `/api/accounting/invoices/purchase`
**Request body:** `application/json` → `CreateInvoiceAccountingRequest` (required)

**Responses:** 200 → `InvoiceAccountingView`


### POST `/api/accounting/invoices/sale`
**Request body:** `application/json` → `CreateInvoiceAccountingRequest` (required)

**Responses:** 200 → `InvoiceAccountingView`


### POST `/api/accounting/invoices/upload`
**Request body:** `application/json` → `CreateInvoiceUploadRequest` (required)

**Responses:** 200 → `InvoiceUploadView`


### GET `/api/accounting/journals`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200


### POST `/api/accounting/journals`
**Request body:** `application/json` → `CreateJournalRequest` (required)

**Responses:** 200 → `JournalView`


### GET `/api/accounting/journals/active`
**Responses:** 200 → `JournalView[]`


### DELETE `/api/accounting/journals/{journalId}`
**Parameters:**
- `journalId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting/journals/{journalId}`
**Parameters:**
- `journalId` (path, string(uuid), required)

**Responses:** 200 → `JournalView`


### PUT `/api/accounting/journals/{journalId}`
**Parameters:**
- `journalId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateJournalRequest` (required)

**Responses:** 200 → `JournalView`


### GET `/api/accounting/journals/{journalId}/comptes`
**Parameters:**
- `journalId` (path, string(uuid), required)

**Responses:** 200 → `AccountView[]`


### GET `/api/accounting/operations/by-no-compte`
**Parameters:**
- `accountNo` (query, string, required)

**Responses:** 200 → `AccountingOperationView[]`


### GET `/api/accounting/operations/search`
**Parameters:**
- `query` (query, string, required)

**Responses:** 200 → `AccountingOperationView[]`


### DELETE `/api/accounting/operations/{operationId}`
**Parameters:**
- `operationId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting/operations/{operationId}`
**Parameters:**
- `operationId` (path, string(uuid), required)

**Responses:** 200 → `AccountingOperationView`


### PUT `/api/accounting/operations/{operationId}`
**Parameters:**
- `operationId` (path, string(uuid), required)

**Request body:** `application/json` → `CreateOperationRequest` (required)

**Responses:** 200 → `AccountingOperationView`


### GET `/api/accounting/plan-comptable`
**Responses:** 200 → `PlanAccountView[]`


### POST `/api/accounting/plan-comptable`
**Request body:** `application/json` → `CreatePlanAccountRequest` (required)

**Responses:** 200 → `PlanAccountView`


### GET `/api/accounting/plan-comptable/actifs`
**Responses:** 200 → `PlanAccountView[]`


### POST `/api/accounting/plan-comptable/admin/init-ohada`
**Responses:** 200 → `PlanAccountView[]`


### GET `/api/accounting/plan-comptable/classe/{classe}`
**Parameters:**
- `classe` (path, string, required)

**Responses:** 200 → `PlanAccountView[]`


### POST `/api/accounting/plan-comptable/import`
**Request body:** `application/json` → `CreatePlanAccountRequest[]` (required)

**Responses:** 200 → `PlanAccountView[]`


### GET `/api/accounting/plan-comptable/prefix/{prefix}`
**Parameters:**
- `prefix` (path, string, required)

**Responses:** 200 → `PlanAccountView[]`


### DELETE `/api/accounting/plan-comptable/{planAccountId}`
**Parameters:**
- `planAccountId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting/plan-comptable/{planAccountId}`
**Parameters:**
- `planAccountId` (path, string(uuid), required)

**Responses:** 200 → `PlanAccountView`


### PUT `/api/accounting/plan-comptable/{planAccountId}`
**Parameters:**
- `planAccountId` (path, string(uuid), required)

**Request body:** `application/json` → `CreatePlanAccountRequest` (required)

**Responses:** 200 → `PlanAccountView`


### POST `/api/accounting/pointage/import`
**Request body:** `application/json` → `CreatePointingRequest` (required)

**Responses:** 200 → `PointingView`


### GET `/api/accounting/settings`
**Responses:** 200 → `AccountingSettingView[]`


### PUT `/api/accounting/settings`
**Request body:** `application/json` → `UpsertSettingRequest` (required)

**Responses:** 200 → `AccountingSettingView`


### GET `/api/accounting/settings/{type}`
**Parameters:**
- `type` (path, string, required)

**Responses:** 200 → `AccountingSettingView`


### GET `/api/accounting/taxes`
**Parameters:**
- `onlyActive` (query, boolean, optional)

**Responses:** 200


### POST `/api/accounting/taxes`
**Request body:** `application/json` → `CreateTaxDefinitionRequest` (required)

**Responses:** 200


### DELETE `/api/accounting/taxes/{taxId}`
**Parameters:**
- `taxId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting/taxes/{taxId}`
**Parameters:**
- `taxId` (path, string(uuid), required)

**Responses:** 200 → `TaxDefinitionView`


### PUT `/api/accounting/taxes/{taxId}`
**Parameters:**
- `taxId` (path, string(uuid), required)

**Request body:** `application/json` → `CreateTaxDefinitionRequest` (required)

**Responses:** 200 → `TaxDefinitionView`


### POST `/api/comptable/lettrage/auto`
**Responses:** 200 → `LetteringView`


### GET `/api/comptable/lettrage/status`
**Responses:** 200 → `object`


### POST `/api/comptable/releve/import/{releveId}`
**Parameters:**
- `releveId` (path, string(uuid), required)

**Responses:** 200 → `object`


### GET `/api/comptable/releve/list`
**Responses:** 200 → `BankStatementPostingView[]`


### POST `/api/comptable/releve/upload`
**Parameters:**
- `compteBancaire` (query, string, required)

**Request body:** `multipart/form-data` → `object`, `application/json` → `CreateBankStatementPostingRequest` (required)

**Responses:** 200


### GET `/api/comptable/stock/impact-comptable/{movementId}`
**Parameters:**
- `movementId` (path, string(uuid), required)

**Responses:** 200 → `StockMovementPostingView`


### POST `/api/comptable/stock/mouvement`
**Request body:** `application/json` → `CreateStockMovementPostingRequest` (required)

**Responses:** 200 → `StockMovementPostingView`


### GET `/api/comptable/stock/mouvements`
**Responses:** 200 → `StockMovementPostingView[]`


### POST `/api/v1/accounting/cash-movements`
**Request body:** `application/json` → `CreateCashRegisterPostingRequest` (required)

**Responses:** 200 → `CashRegisterPostingView`



## accounting-legacy-brouillard-controller

### GET `/api/accounting/brouillards`
**Parameters:**
- `statut` (query, string, optional)
- `type` (query, string, optional)
- `page` (query, integer(int32), optional)
- `size` (query, integer(int32), optional)

**Responses:** 200 → `ApiResponseListBrouillardComptableDto`


### POST `/api/accounting/brouillards/upload`
**Request body:** `multipart/form-data` → `object` (optional)

**Responses:** 200 → `ApiResponseBrouillardComptableDto`


### DELETE `/api/accounting/brouillards/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting/brouillards/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseBrouillardComptableDto`


### POST `/api/accounting/brouillards/{id}/reject`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `BrouillardRejectionRequest` (required)

**Responses:** 200 → `ApiResponseBrouillardComptableDto`


### POST `/api/accounting/brouillards/{id}/validate`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `BrouillardValidationRequest` (optional)

**Responses:** 200 → `ApiResponseBrouillardComptableDto`



## accounting-legacy-operations-controller

### GET `/api/accounting-service/attachments/download/{fileName}`
**Parameters:**
- `fileName` (path, string, required)

**Responses:** 200 → `string(byte)`


### POST `/api/accounting-service/attachments/upload`
**Parameters:**
- `targetType` (query, string, optional)
- `targetId` (query, string(uuid), optional)

**Request body:** `multipart/form-data` → `object`, `application/json` → `CreateAttachmentRequest` (required)

**Responses:** 200 → `AttachmentView`


### GET `/api/accounting-service/exercices`
**Responses:** 200 → `FiscalYearView[]`


### POST `/api/accounting-service/exercices`
**Request body:** `application/json` → `CreateFiscalYearRequest` (required)

**Responses:** 200 → `FiscalYearView`


### GET `/api/accounting-service/exercices/active`
**Responses:** 200 → `FiscalYearView`


### DELETE `/api/accounting-service/exercices/{fiscalYearId}`
**Parameters:**
- `fiscalYearId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting-service/exercices/{fiscalYearId}`
**Parameters:**
- `fiscalYearId` (path, string(uuid), required)

**Responses:** 200 → `FiscalYearView`


### PUT `/api/accounting-service/exercices/{fiscalYearId}`
**Parameters:**
- `fiscalYearId` (path, string(uuid), required)

**Request body:** `application/json` → `CreateFiscalYearRequest` (required)

**Responses:** 200 → `FiscalYearView`


### POST `/api/accounting-service/exercices/{fiscalYearId}/close`
**Parameters:**
- `fiscalYearId` (path, string(uuid), required)

**Responses:** 200 → `FiscalYearView`


### GET `/api/accounting-service/exercices/{fiscalYearId}/periodes`
**Parameters:**
- `fiscalYearId` (path, string(uuid), required)

**Responses:** 200 → `AccountingPeriodView[]`


### GET `/api/accounting-service/immobilisations`
**Responses:** 200 → `FixedAssetView[]`


### POST `/api/accounting-service/immobilisations`
**Request body:** `application/json` → `CreateFixedAssetRequest` (required)

**Responses:** 200 → `FixedAssetView`


### POST `/api/accounting-service/immobilisations/post-depreciation`
**Responses:** 200 → `FixedAssetView[]`


### GET `/api/accounting-service/immobilisations/{fixedAssetId}`
**Parameters:**
- `fixedAssetId` (path, string(uuid), required)

**Responses:** 200 → `FixedAssetView`


### POST `/api/accounting-service/immobilisations/{fixedAssetId}/generate-schedule`
**Parameters:**
- `fixedAssetId` (path, string(uuid), required)

**Responses:** 200 → `object`


### GET `/api/accounting-service/notifications/unread`
**Responses:** 200 → `NotificationView[]`


### POST `/api/accounting-service/notifications/{notificationId}/read`
**Parameters:**
- `notificationId` (path, string(uuid), required)

**Responses:** 200 → `NotificationView`


### GET `/api/accounting-service/periodes`
**Responses:** 200 → `AccountingPeriodView[]`


### POST `/api/accounting-service/periodes`
**Request body:** `application/json` → `CreatePeriodRequest` (required)

**Responses:** 200 → `AccountingPeriodView`


### GET `/api/accounting-service/periodes/by-date`
**Parameters:**
- `date` (query, string(date), required)

**Responses:** 200 → `AccountingPeriodView`


### GET `/api/accounting-service/periodes/code/{code}`
**Parameters:**
- `code` (path, string, required)

**Responses:** 200 → `AccountingPeriodView`


### GET `/api/accounting-service/periodes/non-closed`
**Responses:** 200 → `AccountingPeriodView[]`


### GET `/api/accounting-service/periodes/range`
**Parameters:**
- `startDate` (query, string(date), required)
- `endDate` (query, string(date), required)

**Responses:** 200 → `AccountingPeriodView[]`


### DELETE `/api/accounting-service/periodes/{periodId}`
**Parameters:**
- `periodId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting-service/periodes/{periodId}`
**Parameters:**
- `periodId` (path, string(uuid), required)

**Responses:** 200 → `AccountingPeriodView`


### PUT `/api/accounting-service/periodes/{periodId}`
**Parameters:**
- `periodId` (path, string(uuid), required)

**Request body:** `application/json` → `CreatePeriodRequest` (required)

**Responses:** 200 → `AccountingPeriodView`


### PUT `/api/accounting-service/periodes/{periodId}/close`
**Parameters:**
- `periodId` (path, string(uuid), required)

**Responses:** 200 → `AccountingPeriodView`


### GET `/api/accounting-service/rapport/balance`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `ReportExportView`


### GET `/api/accounting-service/rapport/balance/export/pdf`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `string(byte)`


### GET `/api/accounting-service/rapport/balance/pdf`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `string(byte)`


### GET `/api/accounting-service/rapport/bilan`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `ReportExportView`


### GET `/api/accounting-service/rapport/bilan/export/pdf`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `string(byte)`


### GET `/api/accounting-service/rapport/bilan/pdf`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `string(byte)`


### GET `/api/accounting-service/rapport/compte-resultat`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `ReportExportView`


### GET `/api/accounting-service/rapport/compte-resultat/export/pdf`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `string(byte)`


### GET `/api/accounting-service/rapport/compte-resultat/pdf`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `string(byte)`


### GET `/api/accounting-service/rapport/flux-tresorerie`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `ReportExportView`


### GET `/api/accounting-service/rapport/flux-tresorerie/pdf`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `string(byte)`


### GET `/api/accounting-service/rapport/grand-livre`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `ReportExportView`


### GET `/api/accounting-service/rapport/grand-livre/export/pdf`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `string(byte)`


### GET `/api/accounting-service/rapport/grand-livre/pdf`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `string(byte)`


### GET `/api/accounting-service/rapport/resume-executif`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `ReportExportView`


### GET `/api/accounting-service/rapport/resume-executif/pdf`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `string(byte)`


### POST `/api/accounting-service/tax-declarations/generate`
**Request body:** `application/json` → `CreateTaxDeclarationRequest` (required)

**Responses:** 200 → `TaxDeclarationView`


### GET `/api/accounting-service/tax-declarations/search`
**Parameters:**
- `query` (query, string, required)

**Responses:** 200 → `TaxDeclarationView[]`


### GET `/api/accounting-service/tax-declarations/type/{type}`
**Parameters:**
- `type` (path, string, required)

**Responses:** 200 → `TaxDeclarationView[]`


### DELETE `/api/accounting-service/tax-declarations/{declarationId}`
**Parameters:**
- `declarationId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting-service/tax-declarations/{declarationId}`
**Parameters:**
- `declarationId` (path, string(uuid), required)

**Responses:** 200 → `TaxDeclarationView`


### GET `/api/accounting/attachments/download/{fileName}`
**Parameters:**
- `fileName` (path, string, required)

**Responses:** 200 → `string(byte)`


### POST `/api/accounting/attachments/upload`
**Parameters:**
- `targetType` (query, string, optional)
- `targetId` (query, string(uuid), optional)

**Request body:** `multipart/form-data` → `object`, `application/json` → `CreateAttachmentRequest` (required)

**Responses:** 200 → `AttachmentView`


### GET `/api/accounting/exercices`
**Responses:** 200 → `FiscalYearView[]`


### POST `/api/accounting/exercices`
**Request body:** `application/json` → `CreateFiscalYearRequest` (required)

**Responses:** 200 → `FiscalYearView`


### GET `/api/accounting/exercices/active`
**Responses:** 200 → `FiscalYearView`


### DELETE `/api/accounting/exercices/{fiscalYearId}`
**Parameters:**
- `fiscalYearId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting/exercices/{fiscalYearId}`
**Parameters:**
- `fiscalYearId` (path, string(uuid), required)

**Responses:** 200 → `FiscalYearView`


### PUT `/api/accounting/exercices/{fiscalYearId}`
**Parameters:**
- `fiscalYearId` (path, string(uuid), required)

**Request body:** `application/json` → `CreateFiscalYearRequest` (required)

**Responses:** 200 → `FiscalYearView`


### POST `/api/accounting/exercices/{fiscalYearId}/close`
**Parameters:**
- `fiscalYearId` (path, string(uuid), required)

**Responses:** 200 → `FiscalYearView`


### GET `/api/accounting/exercices/{fiscalYearId}/periodes`
**Parameters:**
- `fiscalYearId` (path, string(uuid), required)

**Responses:** 200 → `AccountingPeriodView[]`


### GET `/api/accounting/immobilisations`
**Responses:** 200 → `FixedAssetView[]`


### POST `/api/accounting/immobilisations`
**Request body:** `application/json` → `CreateFixedAssetRequest` (required)

**Responses:** 200 → `FixedAssetView`


### POST `/api/accounting/immobilisations/post-depreciation`
**Responses:** 200 → `FixedAssetView[]`


### GET `/api/accounting/immobilisations/{fixedAssetId}`
**Parameters:**
- `fixedAssetId` (path, string(uuid), required)

**Responses:** 200 → `FixedAssetView`


### POST `/api/accounting/immobilisations/{fixedAssetId}/generate-schedule`
**Parameters:**
- `fixedAssetId` (path, string(uuid), required)

**Responses:** 200 → `object`


### GET `/api/accounting/notifications/unread`
**Responses:** 200 → `NotificationView[]`


### POST `/api/accounting/notifications/{notificationId}/read`
**Parameters:**
- `notificationId` (path, string(uuid), required)

**Responses:** 200 → `NotificationView`


### GET `/api/accounting/periodes`
**Responses:** 200 → `AccountingPeriodView[]`


### POST `/api/accounting/periodes`
**Request body:** `application/json` → `CreatePeriodRequest` (required)

**Responses:** 200 → `AccountingPeriodView`


### GET `/api/accounting/periodes/by-date`
**Parameters:**
- `date` (query, string(date), required)

**Responses:** 200 → `AccountingPeriodView`


### GET `/api/accounting/periodes/code/{code}`
**Parameters:**
- `code` (path, string, required)

**Responses:** 200 → `AccountingPeriodView`


### GET `/api/accounting/periodes/non-closed`
**Responses:** 200 → `AccountingPeriodView[]`


### GET `/api/accounting/periodes/range`
**Parameters:**
- `startDate` (query, string(date), required)
- `endDate` (query, string(date), required)

**Responses:** 200 → `AccountingPeriodView[]`


### DELETE `/api/accounting/periodes/{periodId}`
**Parameters:**
- `periodId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting/periodes/{periodId}`
**Parameters:**
- `periodId` (path, string(uuid), required)

**Responses:** 200 → `AccountingPeriodView`


### PUT `/api/accounting/periodes/{periodId}`
**Parameters:**
- `periodId` (path, string(uuid), required)

**Request body:** `application/json` → `CreatePeriodRequest` (required)

**Responses:** 200 → `AccountingPeriodView`


### PUT `/api/accounting/periodes/{periodId}/close`
**Parameters:**
- `periodId` (path, string(uuid), required)

**Responses:** 200 → `AccountingPeriodView`


### POST `/api/accounting/tax-declarations/generate`
**Request body:** `application/json` → `CreateTaxDeclarationRequest` (required)

**Responses:** 200 → `TaxDeclarationView`


### GET `/api/accounting/tax-declarations/search`
**Parameters:**
- `query` (query, string, required)

**Responses:** 200 → `TaxDeclarationView[]`


### GET `/api/accounting/tax-declarations/type/{type}`
**Parameters:**
- `type` (path, string, required)

**Responses:** 200 → `TaxDeclarationView[]`


### DELETE `/api/accounting/tax-declarations/{declarationId}`
**Parameters:**
- `declarationId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting/tax-declarations/{declarationId}`
**Parameters:**
- `declarationId` (path, string(uuid), required)

**Responses:** 200 → `TaxDeclarationView`


### POST `/api/comptable/cloture/annuler/{periodeId}`
**Parameters:**
- `periodeId` (path, string(uuid), required)

**Responses:** 200 → `ClosingRunView`


### POST `/api/comptable/cloture/mensuelle/{periodeId}`
**Parameters:**
- `periodeId` (path, string(uuid), required)

**Responses:** 200 → `ClosingRunView`


### GET `/api/comptable/cloture/status/{periodeId}`
**Parameters:**
- `periodeId` (path, string(uuid), required)

**Responses:** 200 → `object`


### POST `/api/comptable/sync/elasticsearch`
**Responses:** 200 → `SynchronizationJobView`


### POST `/api/comptable/sync/redis/clear`
**Responses:** 200 → `object`


### GET `/api/comptable/sync/status`
**Responses:** 200 → `object`


### POST `/api/debug/kafka/test`
**Responses:** 200 → `object`


### GET `/api/debug/organization/info`
**Responses:** 200 → `object`


### DELETE `/api/debug/redis/clear/{key}`
**Parameters:**
- `key` (path, string, required)

**Responses:** 200 → `object`


### GET `/api/debug/redis/test`
**Responses:** 200 → `object`


### POST `/api/debug/redis/test`
**Responses:** 200 → `object`


### POST `/api/debug/sync/test`
**Responses:** 200 → `SynchronizationJobView`



## accounting-legacy-rapport-controller

### GET `/api/accounting/rapport/balance`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `ApiResponseBalanceDesComptesDto`


### GET `/api/accounting/rapport/balance/export/pdf`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `string(byte)`


### GET `/api/accounting/rapport/balance/pdf`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `string(byte)`


### GET `/api/accounting/rapport/bilan`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `ApiResponseBilanDto`


### GET `/api/accounting/rapport/bilan/export/pdf`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `string(byte)`


### GET `/api/accounting/rapport/bilan/pdf`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `string(byte)`


### GET `/api/accounting/rapport/compte-resultat`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `ApiResponseCompteResultatDto`


### GET `/api/accounting/rapport/compte-resultat/export/pdf`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `string(byte)`


### GET `/api/accounting/rapport/compte-resultat/pdf`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `string(byte)`


### GET `/api/accounting/rapport/flux-tresorerie`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `ApiResponseCashFlowDto`


### GET `/api/accounting/rapport/flux-tresorerie/export/pdf`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `string(byte)`


### GET `/api/accounting/rapport/flux-tresorerie/pdf`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `string(byte)`


### GET `/api/accounting/rapport/grand-livre`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `ApiResponseListGrandLivreDto`


### GET `/api/accounting/rapport/grand-livre/export/pdf`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `string(byte)`


### GET `/api/accounting/rapport/grand-livre/pdf`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `string(byte)`


### GET `/api/accounting/rapport/resume-executif`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `ApiResponseExecutiveSummaryDto`


### GET `/api/accounting/rapport/resume-executif/export/pdf`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `string(byte)`


### GET `/api/accounting/rapport/resume-executif/pdf`
**Parameters:**
- `date_debut` (query, string(date), required)
- `date_fin` (query, string(date), required)

**Responses:** 200 → `string(byte)`



## accounting-operations-controller

### GET `/api/accounting-service/attachments`
**Responses:** 200 → `AttachmentView[]`


### POST `/api/accounting-service/attachments`
**Request body:** `application/json` → `CreateAttachmentRequest` (required)

**Responses:** 200 → `AttachmentView`


### GET `/api/accounting-service/closing-runs`
**Responses:** 200 → `ClosingRunView[]`


### POST `/api/accounting-service/closing-runs`
**Request body:** `application/json` → `StartClosingRunRequest` (required)

**Responses:** 200 → `ClosingRunView`


### POST `/api/accounting-service/closing-runs/{runId}/complete`
**Parameters:**
- `runId` (path, string(uuid), required)

**Responses:** 200 → `ClosingRunView`


### GET `/api/accounting-service/dashboard`
**Responses:** 200 → `AccountingDashboardView`


### GET `/api/accounting-service/fiscal-years`
**Responses:** 200 → `FiscalYearView[]`


### POST `/api/accounting-service/fiscal-years`
**Request body:** `application/json` → `CreateFiscalYearRequest` (required)

**Responses:** 200 → `FiscalYearView`


### POST `/api/accounting-service/fiscal-years/{fiscalYearId}/close`
**Parameters:**
- `fiscalYearId` (path, string(uuid), required)

**Responses:** 200 → `FiscalYearView`


### GET `/api/accounting-service/fixed-assets`
**Responses:** 200 → `FixedAssetView[]`


### POST `/api/accounting-service/fixed-assets`
**Request body:** `application/json` → `CreateFixedAssetRequest` (required)

**Responses:** 200 → `FixedAssetView`


### POST `/api/accounting-service/fixed-assets/{fixedAssetId}/depreciate`
**Parameters:**
- `fixedAssetId` (path, string(uuid), required)

**Request body:** `application/json` → `DepreciateFixedAssetRequest` (required)

**Responses:** 200 → `FixedAssetView`


### GET `/api/accounting-service/notifications`
**Responses:** 200 → `NotificationView[]`


### POST `/api/accounting-service/notifications`
**Request body:** `application/json` → `CreateNotificationRequest` (required)

**Responses:** 200 → `NotificationView`


### POST `/api/accounting-service/notifications/{notificationId}/acknowledge`
**Parameters:**
- `notificationId` (path, string(uuid), required)

**Responses:** 200 → `NotificationView`


### GET `/api/accounting-service/periods`
**Responses:** 200 → `AccountingPeriodView[]`


### POST `/api/accounting-service/periods`
**Request body:** `application/json` → `CreatePeriodRequest` (required)

**Responses:** 200 → `AccountingPeriodView`


### POST `/api/accounting-service/periods/{periodId}/close`
**Parameters:**
- `periodId` (path, string(uuid), required)

**Responses:** 200 → `AccountingPeriodView`


### GET `/api/accounting-service/report-exports`
**Responses:** 200 → `ReportExportView[]`


### POST `/api/accounting-service/report-exports`
**Request body:** `application/json` → `GenerateReportExportRequest` (required)

**Responses:** 200 → `ReportExportView`


### GET `/api/accounting-service/synchronization-jobs`
**Responses:** 200 → `SynchronizationJobView[]`


### POST `/api/accounting-service/synchronization-jobs`
**Request body:** `application/json` → `StartSynchronizationJobRequest` (required)

**Responses:** 200 → `SynchronizationJobView`


### POST `/api/accounting-service/synchronization-jobs/{jobId}/complete`
**Parameters:**
- `jobId` (path, string(uuid), required)

**Request body:** `application/json` → `CompleteSynchronizationJobRequest` (required)

**Responses:** 200 → `SynchronizationJobView`


### GET `/api/accounting-service/tax-declarations`
**Responses:** 200 → `TaxDeclarationView[]`


### POST `/api/accounting-service/tax-declarations`
**Request body:** `application/json` → `CreateTaxDeclarationRequest` (required)

**Responses:** 200 → `TaxDeclarationView`


### POST `/api/accounting-service/tax-declarations/{declarationId}/submit`
**Parameters:**
- `declarationId` (path, string(uuid), required)

**Responses:** 200 → `TaxDeclarationView`



## accounting-period-controller

### GET `/api/accounting/periods`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `fiscalYearId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListAccountingPeriodResponse`


### POST `/api/accounting/periods`
**Request body:** `application/json` → `CreateAccountingPeriodRequest` (required)

**Responses:** 200 → `ApiResponseAccountingPeriodResponse`


### POST `/api/accounting/periods/{periodId}/close`
**Parameters:**
- `periodId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseAccountingPeriodResponse`



## accounting-read-controller

### GET `/api/accounting/open-items/payables`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListOpenAccountingItemResponse`


### GET `/api/accounting/open-items/receivables`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListOpenAccountingItemResponse`



## accounting-reference-controller

### GET `/api/accounting-service/reference-data/organizations/my`
**Responses:** 200 → `OrganizationSummaryView[]`


### GET `/api/accounting-service/reference-data/reporting`
**Responses:** 200 → `AccountingReferenceDataView`



## accounting-report-controller

### GET `/api/accounting/reports/aged-balance`
**Parameters:**
- `referenceDate` (query, string(date), optional)

**Responses:** 200 → `AgedBalance`


### GET `/api/accounting/reports/balance-sheet`
**Parameters:**
- `from` (query, string(date), required)
- `to` (query, string(date), required)

**Responses:** 200 → `BalanceSheet`


### GET `/api/accounting/reports/cash-flow`
**Parameters:**
- `from` (query, string(date), required)
- `to` (query, string(date), required)

**Responses:** 200 → `CashFlowStatement`


### GET `/api/accounting/reports/general-ledger`
**Parameters:**
- `from` (query, string(date), required)
- `to` (query, string(date), required)

**Responses:** 200 → `GeneralLedger`


### GET `/api/accounting/reports/income-statement`
**Parameters:**
- `from` (query, string(date), required)
- `to` (query, string(date), required)

**Responses:** 200 → `IncomeStatement`


### GET `/api/accounting/reports/trial-balance`
**Parameters:**
- `from` (query, string(date), required)
- `to` (query, string(date), required)

**Responses:** 200 → `TrialBalance`



## accounting-workflow-controller

### GET `/api/accounting-service/workflows/closing/preview`
**Responses:** 200 → `AccountingClosingPreviewView`



## actor-address-book-controller

### GET `/api/actors/{actorId}/addresses`
**Parameters:**
- `actorId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListAddressResponse`


### POST `/api/actors/{actorId}/addresses`
**Parameters:**
- `actorId` (path, string(uuid), required)

**Request body:** `application/json` → `NestedAddressRequest` (required)

**Responses:** 200 → `ApiResponseAddressResponse`


### DELETE `/api/actors/{actorId}/addresses/{addressId}`
**Parameters:**
- `actorId` (path, string(uuid), required)
- `addressId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### GET `/api/actors/{actorId}/contacts`
**Parameters:**
- `actorId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListContactResponse`


### POST `/api/actors/{actorId}/contacts`
**Parameters:**
- `actorId` (path, string(uuid), required)

**Request body:** `application/json` → `NestedContactRequest` (required)

**Responses:** 200 → `ApiResponseContactResponse`


### DELETE `/api/actors/{actorId}/contacts/{contactId}`
**Parameters:**
- `actorId` (path, string(uuid), required)
- `contactId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`



## actor-controller

### POST `/api/actors`
**Request body:** `application/json` → `CreateActorRequest` (required)

**Responses:** 200 → `ApiResponseActorResponse`


### GET `/api/actors/me`
**Responses:** 200 → `ApiResponseBusinessActorResponse`


### PUT `/api/actors/me`
**Request body:** `application/json` → `BusinessActorRequest` (required)

**Responses:** 200 → `ApiResponseBusinessActorResponse`


### POST `/api/actors/me/reactivate`
**Responses:** 200 → `ApiResponseBusinessActorResponse`


### POST `/api/actors/onboarding`
**Request body:** `application/json` → `BusinessActorRequest` (required)

**Responses:** 200 → `ApiResponseBusinessActorResponse`


### PUT `/api/actors/{actorId}`
**Parameters:**
- `actorId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateActorRequest` (required)

**Responses:** 200 → `ApiResponseActorResponse`



## actor-financial-profile-controller

### POST `/api/third-parties/actors/{actorId}/financial-profile`
**Parameters:**
- `actorId` (path, string(uuid), required)

**Request body:** `application/json` → `EnsureActorFinancialProfileRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`



## address-controller

### GET `/api/addresses`
**Parameters:**
- `X-Tenant-Id` (header, string(uuid), required)
- `addressableType` (query, string, required)
- `addressableId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListAddressResponse`


### POST `/api/addresses`
**Parameters:**
- `X-Tenant-Id` (header, string(uuid), required)

**Request body:** `application/json` → `CreateAddressRequest` (required)

**Responses:** 200 → `ApiResponseAddressResponse`


### DELETE `/api/addresses/{addressId}`
**Parameters:**
- `X-Tenant-Id` (header, string(uuid), required)
- `addressId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`



## administration-controller

### GET `/api/administration/audit`
**Parameters:**
- `limit` (query, integer(int32), optional)

**Responses:** 200 → `ApiResponseListAdministrationAuditResponse`


### GET `/api/administration/governance/agencies`
**Parameters:**
- `organizationId` (query, string(uuid), optional)
- `status` (query, string, optional)

**Responses:** 200 → `ApiResponseListAgencyResponse`


### POST `/api/administration/governance/agencies/{agencyId}`
**Parameters:**
- `agencyId` (path, string(uuid), required)

**Request body:** `application/json` → `GovernanceActionRequest` (required)

**Responses:** 200 → `ApiResponseAgencyResponse`


### GET `/api/administration/governance/business-actors`
**Parameters:**
- `status` (query, string, optional)

**Responses:** 200 → `ApiResponseListBusinessActorResponse`


### POST `/api/administration/governance/business-actors/{businessActorId}`
**Parameters:**
- `businessActorId` (path, string(uuid), required)

**Request body:** `application/json` → `GovernanceActionRequest` (required)

**Responses:** 200 → `ApiResponseBusinessActorResponse`


### GET `/api/administration/governance/organizations`
**Parameters:**
- `status` (query, string, optional)

**Responses:** 200 → `ApiResponseListOrganizationResponse`


### POST `/api/administration/governance/organizations/{organizationId}`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Request body:** `application/json` → `GovernanceActionRequest` (required)

**Responses:** 200 → `ApiResponseOrganizationResponse`


### GET `/api/administration/permissions`
**Responses:** 200 → `ApiResponseListAdministrationPermissionResponse`


### GET `/api/administration/role-templates`
**Responses:** 200 → `ApiResponseListAdministrativeRoleTemplateResponse`


### GET `/api/administration/roles`
**Responses:** 200 → `ApiResponseListAdministrationRoleResponse`


### POST `/api/administration/roles`
**Request body:** `application/json` → `CreateAdministrativeRoleRequest` (required)

**Responses:** 200 → `ApiResponseAdministrationRoleResponse`


### POST `/api/administration/roles/defaults`
**Responses:** 200 → `ApiResponseListAdministrationRoleResponse`


### DELETE `/api/administration/roles/{roleId}`
**Parameters:**
- `roleId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### GET `/api/administration/roles/{roleId}`
**Parameters:**
- `roleId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseAdministrationRoleResponse`


### PATCH `/api/administration/roles/{roleId}`
**Parameters:**
- `roleId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateAdministrativeRoleRequest` (required)

**Responses:** 200 → `ApiResponseAdministrationRoleResponse`


### POST `/api/administration/roles/{roleId}/clone`
**Parameters:**
- `roleId` (path, string(uuid), required)

**Request body:** `application/json` → `CloneAdministrativeRoleRequest` (required)

**Responses:** 200 → `ApiResponseAdministrationRoleResponse`


### PUT `/api/administration/roles/{roleId}/permissions`
**Parameters:**
- `roleId` (path, string(uuid), required)

**Request body:** `application/json` → `ReplaceRolePermissionsRequest` (required)

**Responses:** 200 → `ApiResponseAdministrationRoleResponse`


### GET `/api/administration/settings/general-options`
**Responses:** 200 → `ApiResponseAdministrationGeneralOptionsResponse`


### PUT `/api/administration/settings/general-options`
**Request body:** `application/json` → `UpdateAdministrativeGeneralOptionsRequest` (required)

**Responses:** 200 → `ApiResponseAdministrationGeneralOptionsResponse`


### GET `/api/administration/settings/platform-options`
**Responses:** 200 → `ApiResponseAdministrativePlatformOptionsResponse`


### PUT `/api/administration/settings/platform-options`
**Request body:** `application/json` → `UpdateAdministrativePlatformOptionsRequest` (required)

**Responses:** 200 → `ApiResponseAdministrativePlatformOptionsResponse`


### GET `/api/administration/users`
**Responses:** 200 → `ApiResponseListAdministrationUserResponse`


### GET `/api/administration/users/{userId}/roles`
**Parameters:**
- `userId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListAdministrationUserRoleAssignmentResponse`


### POST `/api/administration/users/{userId}/roles`
**Parameters:**
- `userId` (path, string(uuid), required)

**Request body:** `application/json` → `AssignAdministrativeRoleRequest` (required)

**Responses:** 200 → `ApiResponseAdministrationUserRoleAssignmentResponse`


### DELETE `/api/administration/users/{userId}/roles/{assignmentId}`
**Parameters:**
- `userId` (path, string(uuid), required)
- `assignmentId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`



## advanced-asset-management-controller

### GET `/api/organizations/{organizationId}/advanced-assets`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListAssetProfileResponse`


### GET `/api/organizations/{organizationId}/advanced-assets/overview`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseAdvancedAssetOverview`


### GET `/api/resources/{resourceId}/asset-profile`
**Parameters:**
- `resourceId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseAssetProfileResponse`


### PUT `/api/resources/{resourceId}/asset-profile`
**Parameters:**
- `resourceId` (path, string(uuid), required)

**Request body:** `application/json` → `UpsertAssetProfileRequest` (required)

**Responses:** 200 → `ApiResponseAssetProfileResponse`


### POST `/api/resources/{resourceId}/retire`
**Parameters:**
- `resourceId` (path, string(uuid), required)

**Request body:** `application/json` → `RetireAssetRequest` (required)

**Responses:** 200 → `ApiResponseAssetProfileResponse`



## agency-controller

### GET `/api/organizations/{organizationId}/agencies`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListAgencyResponse`


### POST `/api/organizations/{organizationId}/agencies`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Request body:** `application/json` → `CreateAgencyRequest` (required)

**Responses:** 200 → `ApiResponseAgencyResponse`


### PATCH `/api/organizations/{organizationId}/agencies/{agencyId}`
**Parameters:**
- `agencyId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateAgencyRequest` (required)

**Responses:** 200 → `ApiResponseAgencyResponse`


### POST `/api/organizations/{organizationId}/agencies/{agencyId}/activate`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)

**Request body:** `application/json` → `GovernanceActionRequest` (required)

**Responses:** 200 → `ApiResponseAgencyResponse`


### POST `/api/organizations/{organizationId}/agencies/{agencyId}/close`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)

**Request body:** `application/json` → `GovernanceActionRequest` (required)

**Responses:** 200 → `ApiResponseAgencyResponse`


### POST `/api/organizations/{organizationId}/agencies/{agencyId}/suspend`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)

**Request body:** `application/json` → `GovernanceActionRequest` (required)

**Responses:** 200 → `ApiResponseAgencyResponse`



## agency-schedule-controller

### GET `/api/agencies/{agencyId}/schedule`
**Parameters:**
- `agencyId` (path, string(uuid), required)
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseAgencyScheduleResponse`


### POST `/api/agencies/{agencyId}/schedule/exceptions`
**Parameters:**
- `agencyId` (path, string(uuid), required)
- `organizationId` (query, string(uuid), required)

**Request body:** `application/json` → `OpeningHoursExceptionRequest` (required)

**Responses:** 200 → `ApiResponseOpeningHoursExceptionResponse`


### DELETE `/api/agencies/{agencyId}/schedule/exceptions/{exceptionId}`
**Parameters:**
- `exceptionId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### PUT `/api/agencies/{agencyId}/schedule/regular`
**Parameters:**
- `agencyId` (path, string(uuid), required)
- `organizationId` (query, string(uuid), required)

**Request body:** `application/json` → `RegularOpeningHoursRequest` (required)

**Responses:** 200 → `ApiResponseListOpeningHoursResponse`


### GET `/api/agencies/{agencyId}/schedule/status`
**Parameters:**
- `agencyId` (path, string(uuid), required)
- `organizationId` (query, string(uuid), required)
- `at` (query, string(date-time), optional)

**Responses:** 200 → `ApiResponseAgencyOpenStatusResponse`



## artist-profile-controller

### POST `/api/yowpainter/artists`
**Request body:** `application/json` → `CreateArtistProfileRequest` (required)

**Responses:** 200 → `ApiResponseArtistProfileResponse`


### GET `/api/yowpainter/artists/by-slug/{slug}`
**Parameters:**
- `slug` (path, string, required)

**Responses:** 200 → `ApiResponseArtistProfileResponse`


### GET `/api/yowpainter/artists/{artistProfileId}`
**Parameters:**
- `artistProfileId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseArtistProfileResponse`


### PATCH `/api/yowpainter/artists/{artistProfileId}`
**Parameters:**
- `artistProfileId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateArtistProfileRequest` (required)

**Responses:** 200 → `ApiResponseArtistProfileResponse`



## artwork-controller

### GET `/api/yowpainter/artworks`
**Parameters:**
- `organizationId` (query, string(uuid), optional)
- `artistProfileId` (query, string(uuid), optional)

**Responses:** 200 → `ApiResponseListArtworkResponse`


### POST `/api/yowpainter/artworks`
**Request body:** `application/json` → `CreateArtworkRequest` (required)

**Responses:** 200 → `ApiResponseArtworkResponse`


### GET `/api/yowpainter/artworks/{artworkId}`
**Parameters:**
- `artworkId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseArtworkResponse`


### PATCH `/api/yowpainter/artworks/{artworkId}`
**Parameters:**
- `artworkId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateArtworkRequest` (required)

**Responses:** 200 → `ApiResponseArtworkResponse`


### GET `/api/yowpainter/artworks/{artworkId}/comments`
**Parameters:**
- `artworkId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListArtworkCommentResponse`


### POST `/api/yowpainter/artworks/{artworkId}/comments`
**Parameters:**
- `artworkId` (path, string(uuid), required)

**Request body:** `application/json` → `CommentArtworkRequest` (required)

**Responses:** 200 → `ApiResponseArtworkCommentResponse`


### POST `/api/yowpainter/artworks/{artworkId}/commercialize`
**Parameters:**
- `artworkId` (path, string(uuid), required)

**Request body:** `application/json` → `CommercializeArtworkRequest` (required)

**Responses:** 200 → `ApiResponseArtworkProductLinkResponse`


### POST `/api/yowpainter/artworks/{artworkId}/invoice`
**Parameters:**
- `artworkId` (path, string(uuid), required)

**Request body:** `application/json` → `InvoiceArtworkSaleRequest` (required)

**Responses:** 200 → `ApiResponseArtworkInvoiceLinkResponse`


### GET `/api/yowpainter/artworks/{artworkId}/invoice-link`
**Parameters:**
- `artworkId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseArtworkInvoiceLinkResponse`


### POST `/api/yowpainter/artworks/{artworkId}/invoice/post`
**Parameters:**
- `artworkId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseArtworkInvoiceLinkResponse`


### GET `/api/yowpainter/artworks/{artworkId}/likes`
**Parameters:**
- `artworkId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListArtworkLikeResponse`


### POST `/api/yowpainter/artworks/{artworkId}/likes`
**Parameters:**
- `artworkId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseArtworkLikeResponse`


### GET `/api/yowpainter/artworks/{artworkId}/media`
**Parameters:**
- `artworkId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListArtworkMediaResponse`


### POST `/api/yowpainter/artworks/{artworkId}/media`
**Parameters:**
- `artworkId` (path, string(uuid), required)

**Request body:** `application/json` → `AttachArtworkMediaRequest` (required)

**Responses:** 200 → `ApiResponseArtworkMediaResponse`


### GET `/api/yowpainter/artworks/{artworkId}/product-link`
**Parameters:**
- `artworkId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseArtworkProductLinkResponse`


### POST `/api/yowpainter/artworks/{artworkId}/publish`
**Parameters:**
- `artworkId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseArtworkResponse`


### GET `/api/yowpainter/artworks/{artworkId}/sale-link`
**Parameters:**
- `artworkId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseArtworkSaleLinkResponse`


### POST `/api/yowpainter/artworks/{artworkId}/sale/confirm`
**Parameters:**
- `artworkId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseArtworkSaleLinkResponse`


### POST `/api/yowpainter/artworks/{artworkId}/sell`
**Parameters:**
- `artworkId` (path, string(uuid), required)

**Request body:** `application/json` → `SellArtworkRequest` (required)

**Responses:** 200 → `ApiResponseArtworkSaleLinkResponse`



## asset-portfolio-controller

### GET `/api/organizations/{organizationId}/agencies/{agencyId}/asset-portfolio`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseAssetPortfolioView`


### GET `/api/organizations/{organizationId}/asset-portfolio`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseAssetPortfolioView`


### GET `/api/warehouses/{warehouseId}/asset-portfolio`
**Parameters:**
- `warehouseId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseAssetPortfolioView`



## audit-log-controller

### GET `/api/treasury/audit-logs`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListAuditLogResponse`


### GET `/api/treasury/audit-logs/entity`
**Parameters:**
- `entityId` (query, string(uuid), required)
- `entityType` (query, string, required)

**Responses:** 200 → `ApiResponseListAuditLogResponse`



## auth-controller

### POST `/api/auth/captcha`
**Responses:** 200 → `ApiResponseCaptchaChallengeResponse`


### POST `/api/auth/captcha/verify`
**Request body:** `application/json` → `VerifyCaptchaRequest` (required)

**Responses:** 200 → `ApiResponseCaptchaVerificationResponse`


### POST `/api/auth/discover-contexts`
**Request body:** `application/json` → `LoginRequest` (required)

**Responses:** 200 → `ApiResponseDiscoverLoginContextsResponse`


### POST `/api/auth/discover-sign-up-contexts`
**Request body:** `application/json` → `DiscoverSignUpContextsRequest` (required)

**Responses:** 200 → `ApiResponseDiscoverSignUpContextsResponse`


### POST `/api/auth/email-verification/confirm`
**Request body:** `application/json` → `ConfirmEmailVerificationRequest` (required)

**Responses:** 200 → `ApiResponseUserAccountResponse`


### POST `/api/auth/email-verification/request`
**Responses:** 200 → `ApiResponseIssuedAuthChallengeResponse`


### POST `/api/auth/email-verification/resend`
**Request body:** `application/json` → `object` (required)

**Responses:** 200 → `ApiResponseIssuedAuthChallengeResponse`


### POST `/api/auth/forgot-password`
**Request body:** `application/json` → `ForgotPasswordRequest` (required)

**Responses:** 200 → `ApiResponseForgotPasswordResponse`


### POST `/api/auth/identify`
**Request body:** `application/json` → `IdentifyAccountRequest` (required)

**Responses:** 200 → `ApiResponseIdentifyAccountResponse`


### POST `/api/auth/login`
**Request body:** `application/json` → `LoginRequest` (required)

**Responses:** 200 → `ApiResponseObject`


### POST `/api/auth/login/mfa/confirm`
**Request body:** `application/json` → `ConfirmMfaLoginRequest` (required)

**Responses:** 200 → `ApiResponseLoginResponse`


### POST `/api/auth/mfa/confirm`
**Request body:** `application/json` → `ConfirmMfaRequest` (required)

**Responses:** 200 → `ApiResponseUserAccountResponse`


### POST `/api/auth/mfa/disable`
**Responses:** 200 → `ApiResponseUserAccountResponse`


### POST `/api/auth/mfa/enable`
**Request body:** `application/json` → `EnableMfaRequest` (required)

**Responses:** 200 → `ApiResponseOtpChallengeResponse`


### POST `/api/auth/otp`
**Request body:** `application/json` → `IssueOtpRequest` (required)

**Responses:** 200 → `ApiResponseOtpChallengeResponse`


### POST `/api/auth/otp/verify`
**Request body:** `application/json` → `VerifyOtpRequest` (required)

**Responses:** 200 → `ApiResponseOtpVerificationResponse`


### POST `/api/auth/password-reset/issue`
**Request body:** `application/json` → `IssuePasswordResetRequest` (required)

**Responses:** 200 → `ApiResponseIssuedAuthChallengeResponse`


### POST `/api/auth/phone-verification/confirm`
**Request body:** `application/json` → `ConfirmMfaRequest` (required)

**Responses:** 200 → `ApiResponseUserAccountResponse`


### POST `/api/auth/phone-verification/request`
**Request body:** `application/json` → `IssueOtpRequest` (required)

**Responses:** 200 → `ApiResponseOtpChallengeResponse`


### POST `/api/auth/register`
**Request body:** `application/json` → `RegisterUserRequest` (required)

**Responses:** 200 → `ApiResponseUserAccountResponse`


### POST `/api/auth/reset-password`
**Request body:** `application/json` → `ResetPasswordRequest` (required)

**Responses:** 200 → `ApiResponseUserAccountResponse`


### POST `/api/auth/select-context`
**Request body:** `application/json` → `SelectLoginContextRequest` (required)

**Responses:** 200 → `ApiResponseContextualLoginResponse`


### POST `/api/auth/sign-up`
**Request body:** `application/json` → `PublicSignUpRequest` (required)

**Responses:** 200 → `ApiResponseObject`


### POST `/api/auth/users/{userId}/reset-password`
**Parameters:**
- `userId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseAdminResetPasswordResponse`



## auth-oidc-controller

### GET `/.well-known/oauth-authorization-server`
**Responses:** 200 → `object`


### GET `/.well-known/openid-configuration`
**Responses:** 200 → `object`


### POST `/oauth2/introspect`
**Parameters:**
- `Authorization` (header, string, optional)

**Responses:** 200 → `object`


### POST `/oauth2/token`
**Parameters:**
- `Authorization` (header, string, optional)

**Responses:** 200 → `object`


### GET `/oauth2/userinfo`
**Parameters:**
- `Authorization` (header, string, optional)

**Responses:** 200 → `object`


### POST `/oauth2/userinfo`
**Parameters:**
- `Authorization` (header, string, optional)

**Responses:** 200 → `object`



## bank-account-check-controller

### GET `/api/treasury/bank-accounts/checks`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `bankAccountId` (query, string(uuid), optional)

**Responses:** 200 → `ApiResponseListCheckPaymentResponse`


### POST `/api/treasury/bank-accounts/checks`
**Request body:** `application/json` → `RegisterCheckPaymentRequest` (required)

**Responses:** 200 → `ApiResponseCheckPaymentResponse`


### GET `/api/treasury/bank-accounts/checks/{checkPaymentId}`
**Parameters:**
- `checkPaymentId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseCheckPaymentResponse`


### POST `/api/treasury/bank-accounts/checks/{checkPaymentId}/clear`
**Parameters:**
- `checkPaymentId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseCheckPaymentResponse`



## bank-category-controller

### GET `/api/banking/bank-categories`
**Responses:** 200 → `ApiResponseListBankCategoryResponse`


### POST `/api/banking/bank-categories`
**Request body:** `application/json` → `BankCategoryRequest` (required)

**Responses:** 200 → `ApiResponseBankCategoryResponse`


### GET `/api/banking/bank-categories/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseBankCategoryResponse`


### POST `/api/banking/bank-categories/{id}/deactivate`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseBankCategoryResponse`


### GET `/api/treasury/bank-categories`
**Responses:** 200 → `ApiResponseListBankCategoryResponse`


### POST `/api/treasury/bank-categories`
**Request body:** `application/json` → `BankCategoryRequest` (required)

**Responses:** 200 → `ApiResponseBankCategoryResponse`


### GET `/api/treasury/bank-categories/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseBankCategoryResponse`


### POST `/api/treasury/bank-categories/{id}/deactivate`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseBankCategoryResponse`



## bank-controller

### GET `/api/banking/banks`
**Parameters:**
- `activeOnly` (query, boolean, optional)

**Responses:** 200 → `ApiResponseListBankResponse`


### POST `/api/banking/banks`
**Request body:** `application/json` → `RegisterBankRequest` (required)

**Responses:** 200 → `ApiResponseBankResponse`


### GET `/api/banking/banks/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseBankResponse`


### POST `/api/banking/banks/{id}/deactivate`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseBankResponse`


### GET `/api/treasury/banks`
**Parameters:**
- `activeOnly` (query, boolean, optional)

**Responses:** 200 → `ApiResponseListBankResponse`


### POST `/api/treasury/banks`
**Request body:** `application/json` → `RegisterBankRequest` (required)

**Responses:** 200 → `ApiResponseBankResponse`


### GET `/api/treasury/banks/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseBankResponse`


### POST `/api/treasury/banks/{id}/deactivate`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseBankResponse`



## bank-statement-controller

### GET `/api/banking/statements`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `bankAccountId` (query, string(uuid), optional)

**Responses:** 200 → `ApiResponseListBankStatementResponse`


### POST `/api/banking/statements`
**Request body:** `application/json` → `RegisterBankStatementRequest` (required)

**Responses:** 200 → `ApiResponseBankStatementResponse`


### POST `/api/banking/statements/import`
**Request body:** `application/json` → `RegisterBankStatementRequest` (required)

**Responses:** 200 → `ApiResponseBankStatementResponse`


### POST `/api/banking/statements/upload`
**Request body:** `multipart/form-data` → `object` (optional)

**Responses:** 200 → `ApiResponseStatementUploadResponse`


### GET `/api/banking/statements/{statementId}`
**Parameters:**
- `statementId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseBankStatementResponse`


### POST `/api/banking/statements/{statementId}/close`
**Parameters:**
- `statementId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseBankStatementResponse`


### GET `/api/banking/statements/{statementId}/lines`
**Parameters:**
- `statementId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListStatementLineResponse`


### POST `/api/banking/statements/{statementId}/lines`
**Parameters:**
- `statementId` (path, string(uuid), required)

**Request body:** `application/json` → `ImportStatementLinesRequest` (required)

**Responses:** 200 → `ApiResponseListStatementLineResponse`


### GET `/api/treasury/statements`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `bankAccountId` (query, string(uuid), optional)

**Responses:** 200 → `ApiResponseListBankStatementResponse`


### POST `/api/treasury/statements`
**Request body:** `application/json` → `RegisterBankStatementRequest` (required)

**Responses:** 200 → `ApiResponseBankStatementResponse`


### POST `/api/treasury/statements/import`
**Request body:** `application/json` → `RegisterBankStatementRequest` (required)

**Responses:** 200 → `ApiResponseBankStatementResponse`


### POST `/api/treasury/statements/upload`
**Request body:** `multipart/form-data` → `object` (optional)

**Responses:** 200 → `ApiResponseStatementUploadResponse`


### GET `/api/treasury/statements/{statementId}`
**Parameters:**
- `statementId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseBankStatementResponse`


### POST `/api/treasury/statements/{statementId}/close`
**Parameters:**
- `statementId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseBankStatementResponse`


### GET `/api/treasury/statements/{statementId}/lines`
**Parameters:**
- `statementId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListStatementLineResponse`


### POST `/api/treasury/statements/{statementId}/lines`
**Parameters:**
- `statementId` (path, string(uuid), required)

**Request body:** `application/json` → `ImportStatementLinesRequest` (required)

**Responses:** 200 → `ApiResponseListStatementLineResponse`



## bank-transaction-controller

### GET `/api/treasury/bank-accounts/{bankAccountId}/transactions`
**Parameters:**
- `bankAccountId` (path, string(uuid), required)
- `limit` (query, integer(int32), optional)

**Responses:** 200 → `ApiResponseListBankTransactionResponse`


### POST `/api/treasury/reconciliations/auto/{bankAccountId}`
**Parameters:**
- `bankAccountId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseReconciliationRunResult`


### POST `/api/treasury/reconciliations/manual`
**Request body:** `application/json` → `ManualReconcileBankTransactionRequest` (required)

**Responses:** 200 → `ApiResponseBankTransactionResponse`


### POST `/api/treasury/transactions`
**Request body:** `application/json` → `RegisterBankTransactionRequest` (required)

**Responses:** 200 → `ApiResponseBankTransactionResponse`


### GET `/api/treasury/transactions/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseBankTransactionResponse`


### POST `/api/treasury/transactions/{id}/cancel`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `ReasonRequest` (required)

**Responses:** 200 → `ApiResponseBankTransactionResponse`


### POST `/api/treasury/transactions/{id}/validate`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseBankTransactionResponse`



## billing-legacy-documents-controller

### GET `/api/bon-commande`
**Responses:** 200 → `CommercialDocumentView[]`


### POST `/api/bon-commande`
**Request body:** `application/json` → `CreateCommercialDocumentRequest` (required)

**Responses:** 200 → `CommercialDocumentView`


### GET `/api/bon-commande/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `CommercialDocumentView`


### PUT `/api/bon-commande/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `CreateCommercialDocumentRequest` (required)

**Responses:** 200 → `CommercialDocumentView`


### POST `/api/bon-commande/{id}/payments/bank`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `RecordBankSettlementRequest` (required)

**Responses:** 200 → `PaymentView`


### POST `/api/bon-commande/{id}/payments/cashier`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `RecordCashierPaymentRequest` (required)

**Responses:** 200 → `PaymentView`


### POST `/api/bon-commande/{id}/sync/accounting-invoice`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `SyncDocumentToAccountingRequest` (optional)

**Responses:** 200 → `CommercialDocumentView`


### POST `/api/bon-commande/{id}/sync/cashier-bill`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `CommercialDocumentView`


### GET `/api/bons-achat`
**Responses:** 200 → `CommercialDocumentView[]`


### POST `/api/bons-achat`
**Request body:** `application/json` → `CreateCommercialDocumentRequest` (required)

**Responses:** 200 → `CommercialDocumentView`


### DELETE `/api/bons-achat/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200


### GET `/api/bons-achat/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `CommercialDocumentView`


### PUT `/api/bons-achat/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `CreateCommercialDocumentRequest` (required)

**Responses:** 200 → `CommercialDocumentView`


### POST `/api/bons-achat/{id}/payments/bank`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `RecordBankSettlementRequest` (required)

**Responses:** 200 → `PaymentView`


### POST `/api/bons-achat/{id}/payments/cashier`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `RecordCashierPaymentRequest` (required)

**Responses:** 200 → `PaymentView`


### POST `/api/bons-achat/{id}/sync/accounting-invoice`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `SyncDocumentToAccountingRequest` (optional)

**Responses:** 200 → `CommercialDocumentView`


### POST `/api/bons-achat/{id}/sync/cashier-bill`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `CommercialDocumentView`


### GET `/api/bons-livraison`
**Responses:** 200 → `CommercialDocumentView[]`


### POST `/api/bons-livraison`
**Request body:** `application/json` → `CreateCommercialDocumentRequest` (required)

**Responses:** 200 → `CommercialDocumentView`


### GET `/api/bons-livraison/client/{idClient}`
**Parameters:**
- `idClient` (path, string(uuid), required)

**Responses:** 200 → `CommercialDocumentView[]`


### DELETE `/api/bons-livraison/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200


### GET `/api/bons-livraison/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `CommercialDocumentView`


### PUT `/api/bons-livraison/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `CreateCommercialDocumentRequest` (required)

**Responses:** 200 → `CommercialDocumentView`


### POST `/api/bons-livraison/{id}/effectuer`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `CommercialDocumentView`


### POST `/api/bons-livraison/{id}/payments/bank`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `RecordBankSettlementRequest` (required)

**Responses:** 200 → `PaymentView`


### POST `/api/bons-livraison/{id}/payments/cashier`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `RecordCashierPaymentRequest` (required)

**Responses:** 200 → `PaymentView`


### POST `/api/bons-livraison/{id}/sync/accounting-invoice`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `SyncDocumentToAccountingRequest` (optional)

**Responses:** 200 → `CommercialDocumentView`


### POST `/api/bons-livraison/{id}/sync/cashier-bill`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `CommercialDocumentView`


### GET `/api/facture-fournisseurs`
**Responses:** 200 → `CommercialDocumentView[]`


### POST `/api/facture-fournisseurs`
**Request body:** `application/json` → `CreateCommercialDocumentRequest` (required)

**Responses:** 200 → `CommercialDocumentView`


### GET `/api/facture-fournisseurs/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `CommercialDocumentView`


### PUT `/api/facture-fournisseurs/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `CreateCommercialDocumentRequest` (required)

**Responses:** 200 → `CommercialDocumentView`


### POST `/api/facture-fournisseurs/{id}/payments/bank`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `RecordBankSettlementRequest` (required)

**Responses:** 200 → `PaymentView`


### POST `/api/facture-fournisseurs/{id}/payments/cashier`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `RecordCashierPaymentRequest` (required)

**Responses:** 200 → `PaymentView`


### POST `/api/facture-fournisseurs/{id}/sync/accounting-invoice`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `SyncDocumentToAccountingRequest` (optional)

**Responses:** 200 → `CommercialDocumentView`


### POST `/api/facture-fournisseurs/{id}/sync/cashier-bill`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `CommercialDocumentView`


### GET `/api/factures-proforma`
**Responses:** 200 → `CommercialDocumentView[]`


### POST `/api/factures-proforma`
**Request body:** `application/json` → `CreateCommercialDocumentRequest` (required)

**Responses:** 200 → `CommercialDocumentView`


### GET `/api/factures-proforma/client/{idClient}`
**Parameters:**
- `idClient` (path, string(uuid), required)

**Responses:** 200 → `CommercialDocumentView[]`


### DELETE `/api/factures-proforma/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200


### GET `/api/factures-proforma/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `CommercialDocumentView`


### PUT `/api/factures-proforma/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `CreateCommercialDocumentRequest` (required)

**Responses:** 200 → `CommercialDocumentView`


### POST `/api/factures-proforma/{id}/payments/bank`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `RecordBankSettlementRequest` (required)

**Responses:** 200 → `PaymentView`


### POST `/api/factures-proforma/{id}/payments/cashier`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `RecordCashierPaymentRequest` (required)

**Responses:** 200 → `PaymentView`


### POST `/api/factures-proforma/{id}/sync/accounting-invoice`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `SyncDocumentToAccountingRequest` (optional)

**Responses:** 200 → `CommercialDocumentView`


### POST `/api/factures-proforma/{id}/sync/cashier-bill`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `CommercialDocumentView`


### GET `/api/v1/facturation/bon-receptions`
**Responses:** 200 → `CommercialDocumentView[]`


### POST `/api/v1/facturation/bon-receptions`
**Request body:** `application/json` → `CreateCommercialDocumentRequest` (required)

**Responses:** 200 → `CommercialDocumentView`


### DELETE `/api/v1/facturation/bon-receptions/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200


### GET `/api/v1/facturation/bon-receptions/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `CommercialDocumentView`


### PUT `/api/v1/facturation/bon-receptions/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `CreateCommercialDocumentRequest` (required)

**Responses:** 200 → `CommercialDocumentView`


### POST `/api/v1/facturation/bon-receptions/{id}/payments/bank`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `RecordBankSettlementRequest` (required)

**Responses:** 200 → `PaymentView`


### POST `/api/v1/facturation/bon-receptions/{id}/payments/cashier`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `RecordCashierPaymentRequest` (required)

**Responses:** 200 → `PaymentView`


### POST `/api/v1/facturation/bon-receptions/{id}/sync/accounting-invoice`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `SyncDocumentToAccountingRequest` (optional)

**Responses:** 200 → `CommercialDocumentView`


### POST `/api/v1/facturation/bon-receptions/{id}/sync/cashier-bill`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `CommercialDocumentView`


### GET `/api/v1/facturation/note-credits`
**Responses:** 200 → `CommercialDocumentView[]`


### POST `/api/v1/facturation/note-credits`
**Request body:** `application/json` → `CreateCommercialDocumentRequest` (required)

**Responses:** 200 → `CommercialDocumentView`


### DELETE `/api/v1/facturation/note-credits/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200


### GET `/api/v1/facturation/note-credits/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `CommercialDocumentView`


### PUT `/api/v1/facturation/note-credits/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `CreateCommercialDocumentRequest` (required)

**Responses:** 200 → `CommercialDocumentView`


### POST `/api/v1/facturation/note-credits/{id}/payments/bank`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `RecordBankSettlementRequest` (required)

**Responses:** 200 → `PaymentView`


### POST `/api/v1/facturation/note-credits/{id}/payments/cashier`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `RecordCashierPaymentRequest` (required)

**Responses:** 200 → `PaymentView`


### POST `/api/v1/facturation/note-credits/{id}/sync/accounting-invoice`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `SyncDocumentToAccountingRequest` (optional)

**Responses:** 200 → `CommercialDocumentView`


### POST `/api/v1/facturation/note-credits/{id}/sync/cashier-bill`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `CommercialDocumentView`



## billing-legacy-payments-controller

### GET `/api/paiement`
**Responses:** 200 → `PaymentView[]`


### POST `/api/paiement`
**Request body:** `application/json` → `CreatePaymentRequest` (required)

**Responses:** 200 → `PaymentView`


### GET `/api/paiement/client/{clientId}`
**Parameters:**
- `clientId` (path, string(uuid), required)

**Responses:** 200 → `PaymentView[]`


### GET `/api/paiement/facture/{factureId}`
**Parameters:**
- `factureId` (path, string(uuid), required)

**Responses:** 200 → `PaymentView[]`


### DELETE `/api/paiement/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200


### GET `/api/paiement/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `PaymentView`


### PUT `/api/paiement/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `UpdatePaymentRequest` (required)

**Responses:** 200 → `PaymentView`



## blockchain-controller

### POST `/api/v1/blockchain/anchors`
**Request body:** `application/json` → `AnchorDocumentRequest` (required)

**Responses:** 200 → `ApiResponseTransactionResponse`


### GET `/api/v1/blockchain/blocks`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `chainCode` (query, string, optional)

**Responses:** 200 → `ApiResponseListBlockResponse`


### GET `/api/v1/blockchain/blocks/{blockId}/transactions`
**Parameters:**
- `blockId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListTransactionResponse`


### POST `/api/v1/blockchain/crypto/sign`
**Request body:** `application/json` → `SignPayloadRequest` (required)

**Responses:** 200 → `ApiResponseSignatureResponse`


### POST `/api/v1/blockchain/mine`
**Request body:** `application/json` → `MineBlockRequest` (required)

**Responses:** 200 → `ApiResponseBlockResponse`


### GET `/api/v1/blockchain/transactions`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `chainCode` (query, string, optional)

**Responses:** 200 → `ApiResponseListTransactionResponse`


### POST `/api/v1/blockchain/transactions`
**Request body:** `application/json` → `TransactionRequest` (required)

**Responses:** 200 → `ApiResponseTransactionResponse`


### POST `/api/v1/blockchain/transactions/signing-payload`
**Request body:** `application/json` → `TransactionRequest` (required)

**Responses:** 200 → `ApiResponseSigningPayloadResponse`


### GET `/api/v1/blockchain/validate`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `chainCode` (query, string, optional)

**Responses:** 200 → `ApiResponseChainValidationReport`


### GET `/api/v1/blockchain/wallets`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListWalletResponse`


### POST `/api/v1/blockchain/wallets`
**Request body:** `application/json` → `CreateWalletRequest` (required)

**Responses:** 200 → `ApiResponseGeneratedWalletResponse`



## budget-controller

### POST `/api/accounting/budgets`
**Request body:** `application/json` → `Budget` (required)

**Responses:** 200 → `Budget`


### GET `/api/accounting/budgets/exercice/{exerciceId}`
**Parameters:**
- `exerciceId` (path, string(uuid), required)

**Responses:** 200 → `Budget[]`


### GET `/api/accounting/budgets/exercice/{exerciceId}/vs-realise`
**Parameters:**
- `exerciceId` (path, string(uuid), required)

**Responses:** 200 → `BudgetVsRealise`


### GET `/api/accounting/budgets/periode/{periodeId}`
**Parameters:**
- `periodeId` (path, string(uuid), required)

**Responses:** 200 → `Budget[]`


### DELETE `/api/accounting/budgets/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting/budgets/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `Budget`


### PUT `/api/accounting/budgets/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `Budget` (required)

**Responses:** 200 → `Budget`



## business-domain-controller

### GET `/api/business-domains`
**Responses:** 200 → `ApiResponseListBusinessDomainResponse`


### POST `/api/business-domains`
**Request body:** `application/json` → `CreateBusinessDomainRequest` (required)

**Responses:** 200 → `ApiResponseBusinessDomainResponse`



## cashier-operations-controller

### POST `/api/accounts/transfer`
**Request body:** `application/json` → `TransferRequest` (required)

**Responses:** 200 → `WalletAccountView`


### POST `/api/accounts/transfer-p2p`
**Request body:** `application/json` → `P2PTransferRequest` (required)

**Responses:** 200 → `WalletAccountView`


### POST `/api/accounts/withdraw`
**Request body:** `application/json` → `WithdrawRequest` (required)

**Responses:** 200 → `WalletAccountView`


### GET `/api/admin/accounts`
**Responses:** 200 → `WalletAccountView[]`


### DELETE `/api/admin/assignments`
**Request body:** `application/json` → `DeleteAssignmentRequest` (required)

**Responses:** 200


### GET `/api/admin/assignments`
**Responses:** 200 → `CashierAssignmentView[]`


### POST `/api/admin/assignments`
**Request body:** `application/json` → `CreateAssignmentRequest` (required)

**Responses:** 200 → `CashierAssignmentView`


### DELETE `/api/admin/cashier-agency-assignments`
**Request body:** `application/json` → `DeleteAssignmentRequest` (required)

**Responses:** 200


### GET `/api/admin/cashier-agency-assignments`
**Responses:** 200 → `CashierAssignmentView[]`


### POST `/api/admin/cashier-agency-assignments`
**Request body:** `application/json` → `CreateAssignmentRequest` (required)

**Responses:** 200 → `CashierAssignmentView`


### GET `/api/admin/documents`
**Responses:** 200 → `CashDocumentView[]`


### GET `/api/admin/reconciliations`
**Responses:** 200 → `CashReconciliationView[]`


### GET `/api/audit`
**Responses:** 200 → `CashAuditEntryView[]`


### POST `/api/audit`
**Request body:** `application/json` → `CreateAuditEntryRequest` (required)

**Responses:** 200 → `CashAuditEntryView`


### GET `/api/bills`
**Responses:** 200 → `BillView[]`


### POST `/api/bills`
**Request body:** `application/json` → `CreateBillRequest` (required)

**Responses:** 200 → `BillView`


### POST `/api/bills/import/accounting-invoices/{invoiceId}`
**Parameters:**
- `invoiceId` (path, string(uuid), required)

**Responses:** 200 → `BillView`


### POST `/api/bills/pay`
**Parameters:**
- `billId` (query, string(uuid), required)

**Request body:** `application/json` → `PayBillRequest` (required)

**Responses:** 200 → `BillView`


### POST `/api/bills/{billId}/sync-linked-service`
**Parameters:**
- `billId` (path, string(uuid), required)

**Responses:** 200 → `BillView`


### GET `/api/cash-registers`
**Responses:** 200 → `CashRegisterView[]`


### POST `/api/cash-registers`
**Request body:** `application/json` → `CreateCashRegisterRequest` (required)

**Responses:** 200 → `CashRegisterView`


### DELETE `/api/cash-registers/{registerId}`
**Parameters:**
- `registerId` (path, string(uuid), required)

**Responses:** 200


### GET `/api/cash-registers/{registerId}`
**Parameters:**
- `registerId` (path, string(uuid), required)

**Responses:** 200 → `CashRegisterView`


### PUT `/api/cash-registers/{registerId}`
**Parameters:**
- `registerId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateCashRegisterRequest` (required)

**Responses:** 200 → `CashRegisterView`


### POST `/api/cash-registers/{registerId}/assign`
**Parameters:**
- `registerId` (path, string(uuid), required)

**Request body:** `application/json` → `AssignCashRegisterRequest` (required)

**Responses:** 200 → `CashRegisterView`


### GET `/api/cashier/accounts`
**Responses:** 200 → `WalletAccountView[]`


### GET `/api/cashier/bills`
**Responses:** 200 → `BillView[]`


### POST `/api/cashier/bills/import/accounting-invoices/{invoiceId}`
**Parameters:**
- `invoiceId` (path, string(uuid), required)

**Responses:** 200 → `BillView`


### POST `/api/cashier/bills/{billId}/sync-linked-service`
**Parameters:**
- `billId` (path, string(uuid), required)

**Responses:** 200 → `BillView`


### GET `/api/cashier/bills/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `BillView`


### GET `/api/cashier/fund-requests`
**Responses:** 200 → `FundRequestView[]`


### POST `/api/cashier/fund-requests`
**Request body:** `application/json` → `CreateFundRequest` (required)

**Responses:** 200 → `FundRequestView`


### POST `/api/cashier/fund-requests/{requestId}/fund`
**Parameters:**
- `requestId` (path, string(uuid), required)

**Request body:** `application/json` → `FulfillFundRequestRequest` (required)

**Responses:** 200 → `FundRequestView`


### GET `/api/cashier/movements`
**Responses:** 200 → `CashMovementView[]`


### GET `/api/cashier/notifications`
**Responses:** 200 → `CashNotificationView[]`


### POST `/api/cashier/notifications/test`
**Request body:** `application/json` → `CreateNotificationRequest` (required)

**Responses:** 200 → `CashNotificationView`


### GET `/api/cashier/reconciliations`
**Responses:** 200 → `CashReconciliationView[]`


### GET `/api/cashier/sessions`
**Responses:** 200 → `CashierSessionView[]`


### GET `/api/cashiers`
**Responses:** 200 → `CashierProfileView[]`


### POST `/api/cashiers`
**Request body:** `application/json` → `CreateCashierProfileRequest` (required)

**Responses:** 200 → `CashierProfileView`


### GET `/api/cashiers/available`
**Responses:** 200 → `CashierProfileView[]`


### GET `/api/cashiers/self-profile`
**Parameters:**
- `principalEmail` (query, string, required)

**Responses:** 200 → `CashierProfileView`


### PUT `/api/cashiers/self-profile`
**Parameters:**
- `principalEmail` (query, string, required)

**Request body:** `application/json` → `UpdateMyProfileRequest` (required)

**Responses:** 200 → `CashierProfileView`


### GET `/api/cashiers/with-profile`
**Responses:** 200 → `CashierProfileView[]`


### DELETE `/api/cashiers/{cashierId}`
**Parameters:**
- `cashierId` (path, string(uuid), required)

**Responses:** 200


### PUT `/api/cashiers/{cashierId}`
**Parameters:**
- `cashierId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateCashierProfileRequest` (required)

**Responses:** 200 → `CashierProfileView`


### GET `/api/config/denominations`
**Responses:** 200 → `DenominationView[]`


### GET `/api/dashboard/stat`
**Responses:** 200 → `CashDashboardView`


### GET `/api/dashboard/stats`
**Responses:** 200 → `CashDashboardView`


### GET `/api/lookup/admin`
**Responses:** 200 → `CashierLookupView`


### GET `/api/lookup/cashier`
**Responses:** 200 → `CashierLookupView`


### GET `/api/lookup/customer`
**Responses:** 200 → `object`


### GET `/api/lookup/organization`
**Responses:** 200 → `object`


### POST `/api/movements/transfer`
**Request body:** `application/json` → `CreateMovementRequest` (required)

**Responses:** 200 → `CashMovementView`


### POST `/api/movements/{movementId}/account`
**Parameters:**
- `movementId` (path, string(uuid), required)
- `accountId` (query, string(uuid), required)

**Responses:** 200 → `CashMovementView`


### POST `/api/notify-unauthorized`
**Request body:** `application/json` → `NotifyUnauthorizedRequest` (required)

**Responses:** 200 → `CashNotificationView`


### POST `/api/reconciliations/{reconciliationId}/justify`
**Parameters:**
- `reconciliationId` (path, string(uuid), required)

**Request body:** `application/json` → `JustifyReconciliationRequest` (required)

**Responses:** 200 → `CashReconciliationView`


### POST `/api/reconciliations/{reconciliationId}/review`
**Parameters:**
- `reconciliationId` (path, string(uuid), required)

**Request body:** `application/json` → `ReviewReconciliationRequest` (required)

**Responses:** 200 → `CashReconciliationView`


### GET `/api/reports/audit`
**Responses:** 200 → `CashReportView`


### POST `/api/reports/register/{registerId}`
**Parameters:**
- `registerId` (path, string(uuid), required)

**Responses:** 200 → `CashReportView`


### GET `/api/reports/session/{sessionId}`
**Parameters:**
- `sessionId` (path, string(uuid), required)

**Responses:** 200 → `CashReportView`


### GET `/api/reports/transactions`
**Responses:** 200 → `CashReportView`


### GET `/api/reports/x/{sessionId}`
**Parameters:**
- `sessionId` (path, string(uuid), required)

**Responses:** 200 → `CashReportView`


### POST `/api/reports/z/{sessionId}`
**Parameters:**
- `sessionId` (path, string(uuid), required)

**Responses:** 200 → `CashReportView`


### GET `/api/sessions`
**Responses:** 200 → `CashierSessionView[]`


### POST `/api/sessions`
**Request body:** `application/json` → `CreateSessionRequest` (required)

**Responses:** 200 → `CashierSessionView`


### POST `/api/sessions/{sessionId}/close`
**Parameters:**
- `sessionId` (path, string(uuid), required)

**Request body:** `application/json` → `CloseSessionRequest` (required)

**Responses:** 200 → `CashierSessionView`


### DELETE `/api/sessions/{sessionId}/lock`
**Parameters:**
- `sessionId` (path, string(uuid), required)

**Responses:** 200 → `CashierSessionView`


### POST `/api/sessions/{sessionId}/lock`
**Parameters:**
- `sessionId` (path, string(uuid), required)

**Responses:** 200 → `CashierSessionView`


### GET `/api/transactions`
**Responses:** 200 → `CashMovementView[]`


### GET `/api/transactions/recent`
**Responses:** 200 → `CashMovementView[]`


### GET `/api/users/admins`
**Responses:** 200 → `CashierProfileView[]`


### POST `/api/users/admins`
**Request body:** `application/json` → `CreateCashierProfileRequest` (required)

**Responses:** 200 → `CashierProfileView`


### DELETE `/api/users/admins/{adminId}`
**Parameters:**
- `adminId` (path, string(uuid), required)

**Responses:** 200


### PUT `/api/users/admins/{adminId}`
**Parameters:**
- `adminId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateCashierProfileRequest` (required)

**Responses:** 200 → `CashierProfileView`


### GET `/api/users/cashiers`
**Responses:** 200 → `CashierProfileView[]`


### POST `/api/users/cashiers`
**Request body:** `application/json` → `CreateCashierProfileRequest` (required)

**Responses:** 200 → `CashierProfileView`


### DELETE `/api/users/cashiers/{cashierId}`
**Parameters:**
- `cashierId` (path, string(uuid), required)

**Responses:** 200


### PUT `/api/users/cashiers/{cashierId}`
**Parameters:**
- `cashierId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateCashierProfileRequest` (required)

**Responses:** 200 → `CashierProfileView`


### GET `/api/users/profile`
**Parameters:**
- `principalEmail` (query, string, required)

**Responses:** 200 → `CashierProfileView`



## chart-of-accounts-controller

### GET `/api/accounting/accounts`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListAccountResponse`


### POST `/api/accounting/accounts`
**Request body:** `application/json` → `CreateAccountRequest` (required)

**Responses:** 200 → `ApiResponseAccountResponse`


### POST `/api/accounting/accounts/generate`
**Request body:** `application/json` → `CreateAccountGenerationRequest` (required)

**Responses:** 200 → `ApiResponseAccountResponse`


### GET `/api/accounting/accounts/search`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `prefix` (query, string, required)

**Responses:** 200 → `ApiResponseListAccountResponse`


### GET `/api/accounting/accounts/{accountId}`
**Parameters:**
- `accountId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseAccountResponse`


### PATCH `/api/accounting/accounts/{accountId}`
**Parameters:**
- `accountId` (path, string(uuid), required)

**Request body:** `application/json` → `CreateAccountRequest` (required)

**Responses:** 200 → `ApiResponseAccountResponse`



## check-deposit-controller

### GET `/api/treasury/check-deposits`
**Parameters:**
- `bankAccountId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListCheckDepositResponse`


### POST `/api/treasury/check-deposits`
**Request body:** `application/json` → `CreateCheckDepositRequest` (required)

**Responses:** 200 → `ApiResponseCheckDepositResponse`


### GET `/api/treasury/check-deposits/{depositId}`
**Parameters:**
- `depositId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseCheckDepositResponse`


### POST `/api/treasury/check-deposits/{depositId}/checks`
**Parameters:**
- `depositId` (path, string(uuid), required)

**Request body:** `application/json` → `AddCheckToDepositRequest` (required)

**Responses:** 200 → `ApiResponseCheckDepositResponse`


### DELETE `/api/treasury/check-deposits/{depositId}/checks/{checkId}`
**Parameters:**
- `depositId` (path, string(uuid), required)
- `checkId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseCheckDepositResponse`


### POST `/api/treasury/check-deposits/{depositId}/reject`
**Parameters:**
- `depositId` (path, string(uuid), required)

**Request body:** `application/json` → `ReasonRequest` (required)

**Responses:** 200 → `ApiResponseCheckDepositResponse`


### POST `/api/treasury/check-deposits/{depositId}/submit`
**Parameters:**
- `depositId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseCheckDepositResponse`


### POST `/api/treasury/check-deposits/{depositId}/validate`
**Parameters:**
- `depositId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseCheckDepositResponse`



## check-payment-controller

### GET `/api/treasury/checks`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `bankAccountId` (query, string(uuid), optional)

**Responses:** 200 → `ApiResponseListCheckPaymentResponse`


### POST `/api/treasury/checks`
**Request body:** `application/json` → `RegisterCheckPaymentRequest` (required)

**Responses:** 200 → `ApiResponseCheckPaymentResponse`


### GET `/api/treasury/checks/{checkPaymentId}`
**Parameters:**
- `checkPaymentId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseCheckPaymentResponse`


### POST `/api/treasury/checks/{checkPaymentId}/cancel`
**Parameters:**
- `checkPaymentId` (path, string(uuid), required)

**Request body:** `application/json` → `ReasonRequest` (required)

**Responses:** 200 → `ApiResponseCheckPaymentResponse`


### POST `/api/treasury/checks/{checkPaymentId}/cash`
**Parameters:**
- `checkPaymentId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseCheckPaymentResponse`


### POST `/api/treasury/checks/{checkPaymentId}/deposit`
**Parameters:**
- `checkPaymentId` (path, string(uuid), required)
- `depositId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseCheckPaymentResponse`


### POST `/api/treasury/checks/{checkPaymentId}/issue`
**Parameters:**
- `checkPaymentId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseCheckPaymentResponse`


### POST `/api/treasury/checks/{checkPaymentId}/reject`
**Parameters:**
- `checkPaymentId` (path, string(uuid), required)

**Request body:** `application/json` → `ReasonRequest` (required)

**Responses:** 200 → `ApiResponseCheckPaymentResponse`



## checkbook-controller

### GET `/api/treasury/checkbooks`
**Parameters:**
- `bankAccountId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListCheckbookResponse`


### POST `/api/treasury/checkbooks`
**Request body:** `application/json` → `RegisterCheckbookRequest` (required)

**Responses:** 200 → `ApiResponseCheckbookResponse`


### GET `/api/treasury/checkbooks/{checkbookId}`
**Parameters:**
- `checkbookId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseCheckbookResponse`


### POST `/api/treasury/checkbooks/{checkbookId}/close`
**Parameters:**
- `checkbookId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseCheckbookResponse`


### GET `/api/treasury/checkbooks/{checkbookId}/stats`
**Parameters:**
- `checkbookId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseCheckbookStats`



## client-application-controller

### GET `/api/client-applications`
**Responses:** 200 → `ApiResponseListClientApplicationResponse`


### POST `/api/client-applications`
**Request body:** `application/json` → `CreateClientApplicationRequest` (required)

**Responses:** 200 → `ApiResponseProvisionedClientApplicationResponse`


### GET `/api/client-applications/me`
**Responses:** 200 → `ApiResponseMyClientApplicationResponse`


### GET `/api/client-applications/plans`
**Responses:** 200 → `ApiResponseListClientApplicationPlanResponse`


### POST `/api/client-applications/plans`
**Request body:** `application/json` → `SaveClientApplicationPlanRequest` (required)

**Responses:** 200 → `ApiResponseClientApplicationPlanResponse`


### DELETE `/api/client-applications/plans/{planCode}`
**Parameters:**
- `planCode` (path, string, required)

**Responses:** 200 → `ApiResponseVoid`


### PATCH `/api/client-applications/plans/{planCode}`
**Parameters:**
- `planCode` (path, string, required)

**Request body:** `application/json` → `SaveClientApplicationPlanRequest` (required)

**Responses:** 200 → `ApiResponseClientApplicationPlanResponse`


### PATCH `/api/client-applications/{clientApplicationId}`
**Parameters:**
- `clientApplicationId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateClientApplicationRequest` (required)

**Responses:** 200 → `ApiResponseClientApplicationResponse`


### POST `/api/client-applications/{clientApplicationId}/revoke`
**Parameters:**
- `clientApplicationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseClientApplicationResponse`


### POST `/api/client-applications/{clientApplicationId}/rotate-secret`
**Parameters:**
- `clientApplicationId` (path, string(uuid), required)

**Request body:** `application/json` → `RotateClientApplicationSecretRequest` (optional)

**Responses:** 200 → `ApiResponseProvisionedClientApplicationResponse`



## client-controller

### GET `/api/clients`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `prospect` (query, boolean, optional)

**Responses:** 200 → `ApiResponseListThirdPartyResponse`


### POST `/api/clients`
**Request body:** `application/json` → `CreateCommercialThirdPartyRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### GET `/api/clients/by-accounting-account/{accountingAccount}`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `accountingAccount` (path, string, required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### GET `/api/clients/by-bank-account/{bankAccountNumber}`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `bankAccountNumber` (path, string, required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### GET `/api/clients/search`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `q` (query, string, required)
- `segment` (query, string, optional)
- `minimumQualificationScore` (query, integer(int32), optional)
- `active` (query, boolean, optional)
- `followUpStatus` (query, string, optional)
- `page` (query, integer(int32), optional)
- `size` (query, integer(int32), optional)

**Responses:** 200 → `ApiResponseListThirdPartySearchResponse`


### GET `/api/clients/statistics`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyStatisticsResponse`


### GET `/api/clients/without-accounting-account`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListThirdPartyResponse`


### GET `/api/clients/without-bank-account`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListThirdPartyResponse`


### DELETE `/api/clients/{thirdPartyId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### GET `/api/clients/{thirdPartyId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/clients/{thirdPartyId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateThirdPartyRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/clients/{thirdPartyId}/accounting-account`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `string` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/clients/{thirdPartyId}/activate`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/clients/{thirdPartyId}/bank-account`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `string` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### GET `/api/clients/{thirdPartyId}/bank-accounts`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListThirdPartyBankAccountResponse`


### POST `/api/clients/{thirdPartyId}/bank-accounts`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `ThirdPartyBankAccountRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyBankAccountResponse`


### DELETE `/api/clients/{thirdPartyId}/bank-accounts/{bankAccountId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)
- `bankAccountId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### PATCH `/api/clients/{thirdPartyId}/bank-accounts/{bankAccountId}/set-primary`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)
- `bankAccountId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### PATCH `/api/clients/{thirdPartyId}/deactivate`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/clients/{thirdPartyId}/follow-up/complete`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `ThirdPartyFollowUpRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/clients/{thirdPartyId}/follow-up/schedule`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `ThirdPartyFollowUpRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/clients/{thirdPartyId}/qualification`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `ThirdPartyQualificationRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### POST `/api/clients/{thirdPartyId}/resend-credentials`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### POST `/api/clients/{thirdPartyId}/score/recompute`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`



## contact-controller

### GET `/api/contacts`
**Parameters:**
- `X-Tenant-Id` (header, string(uuid), required)
- `contactableType` (query, string, required)
- `contactableId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListContactResponse`


### POST `/api/contacts`
**Parameters:**
- `X-Tenant-Id` (header, string(uuid), required)

**Request body:** `application/json` → `CreateContactRequest` (required)

**Responses:** 200 → `ApiResponseContactResponse`


### DELETE `/api/contacts/{contactId}`
**Parameters:**
- `X-Tenant-Id` (header, string(uuid), required)
- `contactId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`



## currency-controller

### DELETE `/api/accounting/currencies/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting/currencies/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `Currency`


### PUT `/api/accounting/currencies/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `Currency` (required)

**Responses:** 200 → `Currency`



## customer-controller

### GET `/api/customers`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `prospect` (query, boolean, optional)

**Responses:** 200 → `ApiResponseListThirdPartyResponse`


### POST `/api/customers`
**Request body:** `application/json` → `CreateCommercialThirdPartyRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### GET `/api/customers/by-accounting-account/{accountingAccount}`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `accountingAccount` (path, string, required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### GET `/api/customers/by-bank-account/{bankAccountNumber}`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `bankAccountNumber` (path, string, required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### GET `/api/customers/by-member/{memberId}`
**Parameters:**
- `memberId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### GET `/api/customers/search`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `q` (query, string, required)
- `segment` (query, string, optional)
- `minimumQualificationScore` (query, integer(int32), optional)
- `active` (query, boolean, optional)
- `followUpStatus` (query, string, optional)
- `page` (query, integer(int32), optional)
- `size` (query, integer(int32), optional)

**Responses:** 200 → `ApiResponseListThirdPartySearchResponse`


### GET `/api/customers/statistics`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyStatisticsResponse`


### GET `/api/customers/without-accounting-account`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListThirdPartyResponse`


### GET `/api/customers/without-bank-account`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListThirdPartyResponse`


### DELETE `/api/customers/{id}/link-employee`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### POST `/api/customers/{id}/link-employee/{memberId}`
**Parameters:**
- `id` (path, string(uuid), required)
- `memberId` (path, string(uuid), required)
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### DELETE `/api/customers/{thirdPartyId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### GET `/api/customers/{thirdPartyId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/customers/{thirdPartyId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateThirdPartyRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/customers/{thirdPartyId}/accounting-account`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `string` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/customers/{thirdPartyId}/activate`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/customers/{thirdPartyId}/bank-account`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `string` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### GET `/api/customers/{thirdPartyId}/bank-accounts`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListThirdPartyBankAccountResponse`


### POST `/api/customers/{thirdPartyId}/bank-accounts`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `ThirdPartyBankAccountRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyBankAccountResponse`


### DELETE `/api/customers/{thirdPartyId}/bank-accounts/{bankAccountId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)
- `bankAccountId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### PATCH `/api/customers/{thirdPartyId}/bank-accounts/{bankAccountId}/set-primary`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)
- `bankAccountId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### PATCH `/api/customers/{thirdPartyId}/deactivate`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/customers/{thirdPartyId}/follow-up/complete`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `ThirdPartyFollowUpRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/customers/{thirdPartyId}/follow-up/schedule`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `ThirdPartyFollowUpRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/customers/{thirdPartyId}/qualification`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `ThirdPartyQualificationRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### POST `/api/customers/{thirdPartyId}/resend-credentials`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### POST `/api/customers/{thirdPartyId}/score/recompute`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`



## declaration-controller

### GET `/api/v1/payroll/declarations`
**Parameters:**
- `type` (query, string, required)
- `runId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseDeclarationResponse`


### GET `/api/v1/payroll/declarations/csv`
**Parameters:**
- `type` (query, string, required)
- `runId` (query, string(uuid), required)

**Responses:** 200 → `string`



## department-controller

### GET `/api/spare/departments`
**Parameters:**
- `agencyId` (query, string(uuid), optional)
- `active` (query, boolean, optional)

**Responses:** 200 → `ApiResponseListDepartmentResponse`


### POST `/api/spare/departments`
**Request body:** `application/json` → `CreateDepartmentRequest` (required)

**Responses:** 200 → `ApiResponseDepartmentResponse`


### DELETE `/api/spare/departments/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200


### PATCH `/api/spare/departments/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateDepartmentRequest` (required)

**Responses:** 200 → `ApiResponseDepartmentResponse`


### GET `/api/spare/departments/{id}/members`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListDepartmentMemberResponse`


### POST `/api/spare/departments/{id}/members`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `CreateDepartmentMemberRequest` (required)

**Responses:** 200 → `ApiResponseDepartmentMemberResponse`


### DELETE `/api/spare/departments/{id}/members/{userId}`
**Parameters:**
- `id` (path, string(uuid), required)
- `userId` (path, string(uuid), required)

**Responses:** 200



## document-governance-controller

### POST `/api/document-governance/documents/{documentLinkId}/reviews`
**Parameters:**
- `documentLinkId` (path, string(uuid), required)

**Request body:** `application/json` → `ReviewDocumentRequest` (required)

**Responses:** 200 → `ApiResponseReviewResponse`


### PUT `/api/document-governance/organizations/{organizationId}/agencies/{agencyId}/policies/{targetType}/{documentCategory}`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)
- `targetType` (path, string, required)
- `documentCategory` (path, string, required)

**Request body:** `application/json` → `UpsertPolicyRequest` (required)

**Responses:** 200 → `ApiResponsePolicyResponse`


### GET `/api/document-governance/organizations/{organizationId}/overview`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseDocumentGovernanceOverview`


### PUT `/api/document-governance/organizations/{organizationId}/policies/{targetType}/{documentCategory}`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `targetType` (path, string, required)
- `documentCategory` (path, string, required)

**Request body:** `application/json` → `UpsertPolicyRequest` (required)

**Responses:** 200 → `ApiResponsePolicyResponse`


### GET `/api/document-governance/targets/{targetType}/{targetId}`
**Parameters:**
- `targetType` (path, string, required)
- `targetId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListDocumentStatusView`



## document-hub-controller

### POST `/api/document-hub/links`
**Request body:** `application/json` → `AttachDocumentRequest` (required)

**Responses:** 200 → `ApiResponseDocumentLinkView`


### GET `/api/document-hub/organizations/{organizationId}`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListDocumentLinkView`


### GET `/api/document-hub/organizations/{organizationId}/overview`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseDocumentHubOverview`


### GET `/api/document-hub/targets/{targetType}/{targetId}`
**Parameters:**
- `targetType` (path, string, required)
- `targetId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListDocumentLinkView`



## email-verification-page-controller

### GET `/auth/verify-email`
**Parameters:**
- `token` (query, string, optional)

**Responses:** 200 → `string`



## employee-controller

### GET `/api/employees`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListEmployeeMembershipResponse`


### POST `/api/employees/invite`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Request body:** `application/json` → `InviteEmployeeRequest` (required)

**Responses:** 200 → `ApiResponseEmployeeMembershipResponse`


### GET `/api/employees/roles`
**Responses:** 200 → `ApiResponseListOrganizationRoleResponse`


### DELETE `/api/employees/{membershipId}`
**Parameters:**
- `membershipId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### PUT `/api/employees/{membershipId}`
**Parameters:**
- `membershipId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateEmployeeMembershipRequest` (required)

**Responses:** 200 → `ApiResponseEmployeeMembershipResponse`


### GET `/api/v1/hrm/employees`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `agencyId` (query, string(uuid), optional)

**Responses:** 200 → `ApiResponseListEmployeeResponse`


### POST `/api/v1/hrm/employees`
**Request body:** `application/json` → `CreateEmployeeRequest` (required)

**Responses:** 200 → `ApiResponseEmployeeResponse`


### GET `/api/v1/hrm/employees/check-cnps`
**Parameters:**
- `value` (query, string, required)

**Responses:** 200 → `ApiResponseBoolean`


### PUT `/api/v1/hrm/employees/me/photo`
**Request body:** `application/json` → `SetEmployeePhotoRequest` (required)

**Responses:** 200 → `ApiResponseEmployeeResponse`


### GET `/api/v1/hrm/employees/{employeeId}`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseEmployeeResponse`


### PUT `/api/v1/hrm/employees/{employeeId}`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateEmployeeRequest` (required)

**Responses:** 200 → `ApiResponseEmployeeResponse`


### GET `/api/v1/hrm/employees/{employeeId}/contracts`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListContractResponse`


### POST `/api/v1/hrm/employees/{employeeId}/contracts`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Request body:** `application/json` → `AddContractRequest` (required)

**Responses:** 200 → `ApiResponseContractResponse`


### GET `/api/v1/hrm/employees/{employeeId}/contracts/{contractId}`
**Parameters:**
- `employeeId` (path, string(uuid), required)
- `contractId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseContractResponse`


### POST `/api/v1/hrm/employees/{employeeId}/contracts/{contractId}/renew`
**Parameters:**
- `employeeId` (path, string(uuid), required)
- `contractId` (path, string(uuid), required)

**Request body:** `application/json` → `RenewContractRequest` (required)

**Responses:** 200 → `ApiResponseContractResponse`


### PUT `/api/v1/hrm/employees/{employeeId}/contracts/{contractId}/terminate`
**Parameters:**
- `employeeId` (path, string(uuid), required)
- `contractId` (path, string(uuid), required)

**Request body:** `application/json` → `TerminateContractRequest` (required)

**Responses:** 200 → `ApiResponseContractResponse`


### GET `/api/v1/hrm/employees/{employeeId}/dependents`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListDependentResponse`


### POST `/api/v1/hrm/employees/{employeeId}/dependents`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Request body:** `application/json` → `AddDependentRequest` (required)

**Responses:** 200 → `ApiResponseDependentResponse`


### GET `/api/v1/hrm/employees/{employeeId}/emergency-contacts`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListEmergencyContactResponse`


### POST `/api/v1/hrm/employees/{employeeId}/emergency-contacts`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Request body:** `application/json` → `AddEmergencyContactRequest` (required)

**Responses:** 200 → `ApiResponseEmergencyContactResponse`


### DELETE `/api/v1/hrm/employees/{employeeId}/emergency-contacts/{contactId}`
**Parameters:**
- `employeeId` (path, string(uuid), required)
- `contactId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### PATCH `/api/v1/hrm/employees/{employeeId}/emergency-contacts/{contactId}`
**Parameters:**
- `employeeId` (path, string(uuid), required)
- `contactId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateEmergencyContactRequest` (required)

**Responses:** 200 → `ApiResponseEmergencyContactResponse`


### GET `/api/v1/hrm/employees/{employeeId}/leave-balances`
**Parameters:**
- `employeeId` (path, string(uuid), required)
- `annee` (query, integer(int32), required)

**Responses:** 200 → `ApiResponseListLeaveBalanceResponse`


### GET `/api/v1/hrm/employees/{employeeId}/personal-info`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponsePersonalInfoResponse`


### PUT `/api/v1/hrm/employees/{employeeId}/personal-info`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Request body:** `application/json` → `UpsertPersonalInfoRequest` (required)

**Responses:** 200 → `ApiResponsePersonalInfoResponse`


### PUT `/api/v1/hrm/employees/{employeeId}/photo`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Request body:** `application/json` → `SetEmployeePhotoRequest` (required)

**Responses:** 200 → `ApiResponseEmployeeResponse`


### GET `/api/v1/hrm/employees/{employeeId}/profile`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseEmployeeProfileResponse`


### PUT `/api/v1/hrm/employees/{employeeId}/reactivate`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseEmployeeResponse`


### PUT `/api/v1/hrm/employees/{employeeId}/suspend`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Request body:** `application/json` → `SuspendRequest` (required)

**Responses:** 200 → `ApiResponseEmployeeResponse`


### PUT `/api/v1/hrm/employees/{employeeId}/terminate`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Request body:** `application/json` → `TerminateEmployeeRequest` (required)

**Responses:** 200 → `ApiResponseEmployeeResponse`


### GET `/api/v1/hrm/employees/{employeeId}/timeline`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListTimelineEventResponse`



## employee-self-service-controller

### POST `/api/v1/hrm/employees/me/emergency-contacts`
**Request body:** `application/json` → `AddEmergencyContactRequest` (required)

**Responses:** 200 → `ApiResponseEmergencyContactResponse`


### DELETE `/api/v1/hrm/employees/me/emergency-contacts/{contactId}`
**Parameters:**
- `contactId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### PATCH `/api/v1/hrm/employees/me/emergency-contacts/{contactId}`
**Parameters:**
- `contactId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateEmergencyContactRequest` (required)

**Responses:** 200 → `ApiResponseEmergencyContactResponse`


### PUT `/api/v1/hrm/employees/me/personal-info`
**Request body:** `application/json` → `UpsertPersonalInfoRequest` (required)

**Responses:** 200 → `ApiResponsePersonalInfoResponse`



## exchange-rate-controller

### DELETE `/api/accounting/exchange-rates/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200



## expense-controller

### GET `/api/v1/hrm/expenses`
**Parameters:**
- `employeeId` (query, string(uuid), optional)
- `organizationId` (query, string(uuid), optional)
- `missionOrderId` (query, string(uuid), optional)
- `status` (query, string, optional)

**Responses:** 200 → `ApiResponseListExpenseReportResponse`


### POST `/api/v1/hrm/expenses`
**Request body:** `application/json` → `CreateExpenseReportRequest` (required)

**Responses:** 200 → `ApiResponseExpenseReportResponse`


### GET `/api/v1/hrm/expenses/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseExpenseReportResponse`


### PUT `/api/v1/hrm/expenses/{id}/approve`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseExpenseReportResponse`


### GET `/api/v1/hrm/expenses/{id}/lines`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListExpenseLineResponse`


### POST `/api/v1/hrm/expenses/{id}/lines`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `AddExpenseLineRequest` (required)

**Responses:** 200 → `ApiResponseExpenseLineResponse`


### PUT `/api/v1/hrm/expenses/{id}/reimburse`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseExpenseReportResponse`


### PUT `/api/v1/hrm/expenses/{id}/reject`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseExpenseReportResponse`


### PUT `/api/v1/hrm/expenses/{id}/submit`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseExpenseReportResponse`



## file-controller

### POST `/api/files`
**Parameters:**
- `documentType` (query, string, optional)

**Request body:** `multipart/form-data` → `object` (optional)

**Responses:** 200 → `ApiResponseStoredFileResponse`


### GET `/api/files/{fileId}`
**Parameters:**
- `fileId` (path, string(uuid), required)

**Responses:** 200 → `string(binary)`


### GET `/api/files/{fileId}/content`
**Parameters:**
- `fileId` (path, string(uuid), required)

**Responses:** 200 → `string(binary)`


### GET `/api/files/{fileId}/metadata`
**Parameters:**
- `fileId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseStoredFileResponse`


### GET `/api/files/{fileId}/review`
**Parameters:**
- `fileId` (path, string(uuid), required)

**Responses:** 200 → `string(binary)`



## final-settlement-controller

### GET `/api/v1/payroll/final-settlements`
**Parameters:**
- `employeeId` (query, string(uuid), optional)
- `organizationId` (query, string(uuid), optional)

**Responses:** 200 → `ApiResponseListFinalSettlementResponse`


### POST `/api/v1/payroll/final-settlements`
**Request body:** `application/json` → `CalculateRequest` (required)

**Responses:** 200 → `ApiResponseFinalSettlementResponse`


### GET `/api/v1/payroll/final-settlements/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseFinalSettlementResponse`


### PUT `/api/v1/payroll/final-settlements/{id}/pay`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseFinalSettlementResponse`



## fiscal-year-controller

### GET `/api/accounting/fiscal-years`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListFiscalYearResponse`


### POST `/api/accounting/fiscal-years`
**Request body:** `application/json` → `CreateFiscalYearRequest` (required)

**Responses:** 200 → `ApiResponseFiscalYearResponse`


### GET `/api/accounting/fiscal-years/{fiscalYearId}`
**Parameters:**
- `fiscalYearId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseFiscalYearResponse`


### POST `/api/accounting/fiscal-years/{fiscalYearId}/close`
**Parameters:**
- `fiscalYearId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseFiscalYearResponse`



## fixed-asset-controller

### GET `/api/accounting/fixed-assets`
**Parameters:**
- `status` (query, string, optional)

**Responses:** 200 → `FixedAsset[]`


### POST `/api/accounting/fixed-assets`
**Request body:** `application/json` → `FixedAsset` (required)

**Responses:** 200 → `FixedAsset`


### DELETE `/api/accounting/fixed-assets/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting/fixed-assets/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `FixedAsset`


### PUT `/api/accounting/fixed-assets/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `FixedAsset` (required)

**Responses:** 200 → `FixedAsset`


### POST `/api/accounting/fixed-assets/{id}/discard`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `FixedAsset`


### POST `/api/accounting/fixed-assets/{id}/sell`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `FixedAsset` (required)

**Responses:** 200 → `FixedAsset`



## gallery-event-controller

### GET `/api/yowpainter/events`
**Parameters:**
- `organizationId` (query, string(uuid), optional)
- `artistProfileId` (query, string(uuid), optional)

**Responses:** 200 → `ApiResponseListGalleryEventResponse`


### POST `/api/yowpainter/events`
**Request body:** `application/json` → `CreateGalleryEventRequest` (required)

**Responses:** 200 → `ApiResponseGalleryEventResponse`


### POST `/api/yowpainter/events/tickets/validate`
**Request body:** `application/json` → `ValidateGalleryTicketRequest` (required)

**Responses:** 200 → `ApiResponseGalleryTicketResponse`


### GET `/api/yowpainter/events/{eventId}`
**Parameters:**
- `eventId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseGalleryEventResponse`


### GET `/api/yowpainter/events/{eventId}/reservations`
**Parameters:**
- `eventId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListGalleryReservationResponse`


### POST `/api/yowpainter/events/{eventId}/reservations`
**Parameters:**
- `eventId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseGalleryReservationResponse`



## garnishment-controller

### GET `/api/v1/payroll/garnishments`
**Parameters:**
- `employeeId` (query, string(uuid), optional)
- `organizationId` (query, string(uuid), optional)

**Responses:** 200 → `ApiResponseListGarnishmentResponse`


### POST `/api/v1/payroll/garnishments`
**Request body:** `application/json` → `CreateRequest` (required)

**Responses:** 200 → `ApiResponseGarnishmentResponse`


### DELETE `/api/v1/payroll/garnishments/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseGarnishmentResponse`


### GET `/api/v1/payroll/garnishments/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseGarnishmentResponse`


### PUT `/api/v1/payroll/garnishments/{id}/resume`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseGarnishmentResponse`


### PUT `/api/v1/payroll/garnishments/{id}/suspend`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseGarnishmentResponse`



## general-options-controller

### GET `/api/general-options`
**Responses:** 200 → `ApiResponseAppBusinessSettingsResponse`


### PUT `/api/general-options`
**Request body:** `application/json` → `UpdateAppBusinessSettingsRequest` (required)

**Responses:** 200 → `ApiResponseAppBusinessSettingsResponse`


### GET `/api/general-options/api/settings/agency/{agencyId}`
**Parameters:**
- `agencyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseAppBusinessSettingsResponse`


### PUT `/api/general-options/api/settings/agency/{agencyId}`
**Parameters:**
- `agencyId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateAppBusinessSettingsRequest` (required)

**Responses:** 200 → `ApiResponseAppBusinessSettingsResponse`


### GET `/api/general-options/api/settings/global`
**Responses:** 200 → `ApiResponseAppBusinessSettingsResponse`


### PUT `/api/general-options/api/settings/global`
**Request body:** `application/json` → `UpdateAppBusinessSettingsRequest` (required)

**Responses:** 200 → `ApiResponseAppBusinessSettingsResponse`


### GET `/api/generalOptions`
**Responses:** 200 → `ApiResponseAppBusinessSettingsResponse`


### PUT `/api/generalOptions`
**Request body:** `application/json` → `UpdateAppBusinessSettingsRequest` (required)

**Responses:** 200 → `ApiResponseAppBusinessSettingsResponse`


### GET `/api/generalOptions/api/settings/agency/{agencyId}`
**Parameters:**
- `agencyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseAppBusinessSettingsResponse`


### PUT `/api/generalOptions/api/settings/agency/{agencyId}`
**Parameters:**
- `agencyId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateAppBusinessSettingsRequest` (required)

**Responses:** 200 → `ApiResponseAppBusinessSettingsResponse`


### GET `/api/generalOptions/api/settings/global`
**Responses:** 200 → `ApiResponseAppBusinessSettingsResponse`


### PUT `/api/generalOptions/api/settings/global`
**Request body:** `application/json` → `UpdateAppBusinessSettingsRequest` (required)

**Responses:** 200 → `ApiResponseAppBusinessSettingsResponse`


### GET `/api/settings/agency/{agencyId}`
**Parameters:**
- `agencyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseAppBusinessSettingsResponse`


### PUT `/api/settings/agency/{agencyId}`
**Parameters:**
- `agencyId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateAppBusinessSettingsRequest` (required)

**Responses:** 200 → `ApiResponseAppBusinessSettingsResponse`


### GET `/api/settings/global`
**Responses:** 200 → `ApiResponseAppBusinessSettingsResponse`


### PUT `/api/settings/global`
**Request body:** `application/json` → `UpdateAppBusinessSettingsRequest` (required)

**Responses:** 200 → `ApiResponseAppBusinessSettingsResponse`



## generalized-inventory-campaign-controller

### GET `/api/organizations/{organizationId}/generalized-inventory-campaigns`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (query, string(uuid), optional)

**Responses:** 200 → `ApiResponseListCampaignResponse`


### POST `/api/organizations/{organizationId}/generalized-inventory-campaigns`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Request body:** `application/json` → `PlanCampaignRequest` (required)

**Responses:** 200 → `ApiResponseCampaignResponse`


### POST `/api/organizations/{organizationId}/generalized-inventory-campaigns/{campaignId}/approve`
**Parameters:**
- `campaignId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseCampaignResponse`


### POST `/api/organizations/{organizationId}/generalized-inventory-campaigns/{campaignId}/start`
**Parameters:**
- `campaignId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseCampaignResponse`


### POST `/api/organizations/{organizationId}/generalized-inventory-campaigns/{campaignId}/submit`
**Parameters:**
- `campaignId` (path, string(uuid), required)

**Request body:** `application/json` → `SubmitCampaignRequest` (required)

**Responses:** 200 → `ApiResponseCampaignResponse`



## inventory-controller

### GET `/api/inventory/movements`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `agencyId` (query, string(uuid), required)
- `productId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListStockMovementResponse`


### POST `/api/inventory/movements`
**Request body:** `application/json` → `RecordStockMovementRequest` (required)

**Responses:** 200 → `ApiResponseStockMovementResponse`


### GET `/api/inventory/movements/balance`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `agencyId` (query, string(uuid), required)
- `productId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseStockBalanceResponse`


### GET `/api/inventory/movements/{movementId}`
**Parameters:**
- `movementId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseStockMovementResponse`


### POST `/api/inventory/movements/{movementId}/validate`
**Parameters:**
- `movementId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseStockMovementResponse`



## inventory-session-controller

### GET `/api/inventories`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListInventorySessionResponse`


### POST `/api/inventories`
**Request body:** `application/json` → `CreateInventorySessionRequest` (required)

**Responses:** 200 → `ApiResponseInventorySessionResponse`


### POST `/api/inventories/{inventoryId}/validate`
**Parameters:**
- `inventoryId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseInventorySessionResponse`


### GET `/api/inventory/sessions`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListInventorySessionResponse`


### POST `/api/inventory/sessions`
**Request body:** `application/json` → `CreateInventorySessionRequest` (required)

**Responses:** 200 → `ApiResponseInventorySessionResponse`


### POST `/api/inventory/sessions/{inventoryId}/validate`
**Parameters:**
- `inventoryId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseInventorySessionResponse`



## jwk-set-controller

### GET `/.well-known/jwks.json`
**Responses:** 200 → `object`



## kyc-verification-controller

### POST `/api/kyc/verify`
**Request body:** `multipart/form-data` → `object` (optional)

**Responses:** 200 → `DocumentAnalysisResponse`



## leave-controller

### GET `/api/v1/hrm/leaves`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `agencyId` (query, string(uuid), optional)

**Responses:** 200 → `ApiResponseListLeaveResponse`


### POST `/api/v1/hrm/leaves`
**Request body:** `application/json` → `SubmitLeaveRequest` (required)

**Responses:** 200 → `ApiResponseLeaveResponse`


### POST `/api/v1/hrm/leaves/accrual/run`
**Responses:** 200 → `ApiResponseInteger`


### GET `/api/v1/hrm/leaves/employee/{employeeId}`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListLeaveResponse`


### GET `/api/v1/hrm/leaves/pending`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `agencyId` (query, string(uuid), optional)

**Responses:** 200 → `ApiResponseListLeaveResponse`


### GET `/api/v1/hrm/leaves/{leaveRequestId}`
**Parameters:**
- `leaveRequestId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseLeaveResponse`


### PUT `/api/v1/hrm/leaves/{leaveRequestId}/approve`
**Parameters:**
- `leaveRequestId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseLeaveResponse`


### PUT `/api/v1/hrm/leaves/{leaveRequestId}/cancel`
**Parameters:**
- `leaveRequestId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseLeaveResponse`


### PUT `/api/v1/hrm/leaves/{leaveRequestId}/reject`
**Parameters:**
- `leaveRequestId` (path, string(uuid), required)

**Request body:** `application/json` → `RejectLeaveRequest` (required)

**Responses:** 200 → `ApiResponseLeaveResponse`



## ledger-controller

### GET `/api/accounting/ledgers`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `type` (query, string, optional)

**Responses:** 200 → `ApiResponseListLedgerResponse`


### POST `/api/accounting/ledgers`
**Request body:** `application/json` → `CreateLedgerRequest` (required)

**Responses:** 200 → `ApiResponseLedgerResponse`


### GET `/api/accounting/ledgers/{ledgerId}`
**Parameters:**
- `ledgerId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseLedgerResponse`



## legacy-banking-catalog-controller

### GET `/api/audit-logs`
**Responses:** 200 → `AuditLogView[]`


### GET `/api/audit-logs/actions`
**Responses:** 200 → `string[]`


### GET `/api/audit-logs/count`
**Responses:** 200 → `integer(int64)`


### GET `/api/audit-logs/entity/{entityId}`
**Parameters:**
- `entityId` (path, string(uuid), required)

**Responses:** 200 → `AuditLogView[]`


### GET `/api/audit-logs/modules`
**Responses:** 200 → `string[]`


### GET `/api/audit-logs/today`
**Responses:** 200 → `AuditLogView[]`


### GET `/api/audit-logs/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `AuditLogView`


### GET `/api/banks`
**Responses:** 200 → `BankView[]`


### POST `/api/banks`
**Request body:** `application/json` → `CreateBankRequest` (required)

**Responses:** 200 → `BankView`


### GET `/api/banks/code/{code}`
**Parameters:**
- `code` (path, string, required)

**Responses:** 200 → `BankView`


### DELETE `/api/banks/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200


### GET `/api/banks/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `BankView`


### PUT `/api/banks/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateBankRequest` (required)

**Responses:** 200 → `BankView`


### POST `/api/statement-lines`
**Request body:** `application/json` → `CreateStatementLineRequest` (required)

**Responses:** 200 → `StatementLineView`


### GET `/api/statement-lines/statement/{statementId}`
**Parameters:**
- `statementId` (path, string(uuid), required)

**Responses:** 200 → `StatementLineView[]`


### POST `/api/statement-lines/statement/{statementId}/batch`
**Parameters:**
- `statementId` (path, string(uuid), required)

**Request body:** `application/json` → `BatchStatementLinesRequest` (required)

**Responses:** 200 → `StatementLineView[]`


### GET `/api/statement-lines/statement/{statementId}/matched`
**Parameters:**
- `statementId` (path, string(uuid), required)

**Responses:** 200 → `StatementLineView[]`


### GET `/api/statement-lines/statement/{statementId}/unmatched`
**Parameters:**
- `statementId` (path, string(uuid), required)

**Responses:** 200 → `StatementLineView[]`


### DELETE `/api/statement-lines/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200


### GET `/api/statement-lines/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `StatementLineView`


### POST `/api/statement-lines/{id}/ignore`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `StatementLineView`


### POST `/api/statement-lines/{id}/reset`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `StatementLineView`


### GET `/api/transaction-types`
**Responses:** 200 → `TransactionTypeView[]`


### POST `/api/transaction-types`
**Request body:** `application/json` → `CreateTransactionTypeRequest` (required)

**Responses:** 200 → `TransactionTypeView`


### GET `/api/transaction-types/category/{category}`
**Parameters:**
- `category` (path, string, required)

**Responses:** 200 → `TransactionTypeView[]`


### DELETE `/api/transaction-types/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200


### GET `/api/transaction-types/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `TransactionTypeView`


### PUT `/api/transaction-types/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateTransactionTypeRequest` (required)

**Responses:** 200 → `TransactionTypeView`



## legacy-banking-controller

### GET `/api/banking/accounts`
**Parameters:**
- `organizationId` (query, string(uuid), optional)
- `agencyId` (query, string(uuid), optional)

**Responses:** 200 → `ApiResponseListBankAccountResponse`


### POST `/api/banking/accounts`
**Request body:** `application/json` → `RegisterBankAccountRequest` (required)

**Responses:** 200 → `ApiResponseBankAccountResponse`


### GET `/api/banking/accounts/{bankAccountId}`
**Parameters:**
- `bankAccountId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseBankAccountResponse`


### GET `/api/banking/accounts/{bankAccountId}/transactions`
**Parameters:**
- `bankAccountId` (path, string(uuid), required)
- `limit` (query, integer(int32), optional)

**Responses:** 200 → `ApiResponseListBankTransactionResponse`


### POST `/api/banking/transactions`
**Request body:** `application/json` → `RegisterBankTransactionRequest` (required)

**Responses:** 200 → `ApiResponseBankTransactionResponse`



## legacy-check-controller

### GET `/api/banking/checks`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `status` (query, string, optional)
- `bankAccountId` (query, string(uuid), optional)

**Responses:** 200 → `ApiResponseListCheckPaymentResponse`


### POST `/api/banking/checks`
**Request body:** `application/json` → `RegisterCheckPaymentRequest` (required)

**Responses:** 200 → `ApiResponseCheckPaymentResponse`


### POST `/api/banking/checks/{checkPaymentId}/deposit`
**Parameters:**
- `checkPaymentId` (path, string(uuid), required)
- `accountId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseCheckPaymentResponse`



## legacy-point-of-interest-controller

### GET `/api/pois`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListPointOfInterestResponse`


### POST `/api/pois`
**Request body:** `application/json` → `CreatePointOfInterestRequest` (required)

**Responses:** 200 → `ApiResponsePointOfInterestResponse`


### DELETE `/api/pois/link`
**Parameters:**
- `agencyId` (query, string(uuid), required)
- `poiId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### POST `/api/pois/link`
**Request body:** `application/json` → `LinkPointOfInterestRequest` (required)

**Responses:** 200 → `ApiResponseVoid`



## legacy-reconciliation-controller

### POST `/api/banking/reconciliation/auto/{accountId}`
**Parameters:**
- `accountId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### POST `/api/banking/reconciliation/manual`
**Request body:** `application/json` → `ManualReconcileBankTransactionRequest` (required)

**Responses:** 200 → `ApiResponseBankTransactionResponse`



## loan-advance-controller

### GET `/api/v1/hrm/loan-advances`
**Responses:** 200 → `ApiResponseListLoanAdvanceResponse`


### POST `/api/v1/hrm/loan-advances`
**Request body:** `application/json` → `RequestLoanAdvanceRequest` (required)

**Responses:** 200 → `ApiResponseLoanAdvanceResponse`


### GET `/api/v1/hrm/loan-advances/employee/{employeeId}`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListLoanAdvanceResponse`


### GET `/api/v1/hrm/loan-advances/mine`
**Responses:** 200 → `ApiResponseListLoanAdvanceResponse`


### POST `/api/v1/hrm/loan-advances/mine`
**Request body:** `application/json` → `MyLoanRequest` (required)

**Responses:** 200 → `ApiResponseLoanAdvanceResponse`


### GET `/api/v1/hrm/loan-advances/mine/{loanAdvanceId}/repayments`
**Parameters:**
- `loanAdvanceId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListLoanRepaymentResponse`


### GET `/api/v1/hrm/loan-advances/{loanAdvanceId}`
**Parameters:**
- `loanAdvanceId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseLoanAdvanceResponse`


### PUT `/api/v1/hrm/loan-advances/{loanAdvanceId}/approve`
**Parameters:**
- `loanAdvanceId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseLoanAdvanceResponse`


### PUT `/api/v1/hrm/loan-advances/{loanAdvanceId}/reject`
**Parameters:**
- `loanAdvanceId` (path, string(uuid), required)

**Request body:** `application/json` → `RejectLoanRequest` (required)

**Responses:** 200 → `ApiResponseLoanAdvanceResponse`


### GET `/api/v1/hrm/loan-advances/{loanAdvanceId}/repayments`
**Parameters:**
- `loanAdvanceId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListLoanRepaymentResponse`



## lookup-table-admin-controller

### GET `/api/v1/payroll/lookup-tables`
**Parameters:**
- `countryCode` (query, string, required)

**Responses:** 200 → `ApiResponseListLookupTableResponse`


### POST `/api/v1/payroll/lookup-tables`
**Request body:** `application/json` → `CreateLookupTableRequest` (required)

**Responses:** 200 → `ApiResponseLookupTableResponse`


### DELETE `/api/v1/payroll/lookup-tables/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseLookupTableResponse`


### GET `/api/v1/payroll/lookup-tables/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseLookupTableResponse`


### PUT `/api/v1/payroll/lookup-tables/{id}/activate`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseLookupTableResponse`



## material-request-controller

### GET `/api/spare/material-requests`
**Parameters:**
- `agencyId` (query, string(uuid), optional)
- `departmentId` (query, string(uuid), optional)
- `status` (query, string, optional)

**Responses:** 200 → `ApiResponseListMaterialRequestResponse`


### POST `/api/spare/material-requests`
**Request body:** `application/json` → `CreateMaterialRequestRequest` (required)

**Responses:** 200 → `ApiResponseMaterialRequestResponse`


### POST `/api/spare/material-requests/{id}/approve`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseMaterialRequestResponse`


### POST `/api/spare/material-requests/{id}/close`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `CloseMaterialRequestRequest` (optional)

**Responses:** 200 → `ApiResponseMaterialRequestResponse`


### POST `/api/spare/material-requests/{id}/issue`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `MaterialActionRequest` (required)

**Responses:** 200 → `ApiResponseMaterialRequestResponse`


### POST `/api/spare/material-requests/{id}/reject`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `RejectMaterialRequestRequest` (optional)

**Responses:** 200 → `ApiResponseMaterialRequestResponse`


### POST `/api/spare/material-requests/{id}/return`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `MaterialActionRequest` (required)

**Responses:** 200 → `ApiResponseMaterialRequestResponse`



## medical-controller

### GET `/api/v1/hrm/medical/certificates`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListMedicalCertificateResponse`


### POST `/api/v1/hrm/medical/certificates`
**Request body:** `application/json` → `CreateMedicalCertificateRequest` (required)

**Responses:** 200 → `ApiResponseMedicalCertificateResponse`


### GET `/api/v1/hrm/medical/certificates/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseMedicalCertificateResponse`


### GET `/api/v1/hrm/medical/employees/{employeeId}/certificates`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListMedicalCertificateResponse`


### GET `/api/v1/hrm/medical/employees/{employeeId}/visits`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListMedicalVisitResponse`


### GET `/api/v1/hrm/medical/visits`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListMedicalVisitResponse`


### POST `/api/v1/hrm/medical/visits`
**Request body:** `application/json` → `CreateMedicalVisitRequest` (required)

**Responses:** 200 → `ApiResponseMedicalVisitResponse`


### GET `/api/v1/hrm/medical/visits/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseMedicalVisitResponse`



## medical-self-service-controller

### POST `/api/v1/hrm/medical/me/certificates`
**Request body:** `application/json` → `SubmitMyCertificateRequest` (required)

**Responses:** 200 → `ApiResponseMedicalCertificateResponse`



## mission-order-controller

### GET `/api/v1/hrm/mission-orders`
**Parameters:**
- `employeeId` (query, string(uuid), optional)
- `organizationId` (query, string(uuid), optional)
- `status` (query, string, optional)

**Responses:** 200 → `ApiResponseListMissionOrderResponse`


### POST `/api/v1/hrm/mission-orders`
**Request body:** `application/json` → `CreateMissionOrderRequest` (required)

**Responses:** 200 → `ApiResponseMissionOrderResponse`


### GET `/api/v1/hrm/mission-orders/declined`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListMissionOrderResponse`


### GET `/api/v1/hrm/mission-orders/pending-acceptance`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListMissionOrderResponse`


### GET `/api/v1/hrm/mission-orders/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseMissionOrderResponse`


### PUT `/api/v1/hrm/mission-orders/{id}/accept`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseMissionOrderResponse`


### POST `/api/v1/hrm/mission-orders/{id}/amend`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `AmendMissionOrderRequest` (required)

**Responses:** 200 → `ApiResponseMissionOrderResponse`


### PUT `/api/v1/hrm/mission-orders/{id}/cancel`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseMissionOrderResponse`


### PUT `/api/v1/hrm/mission-orders/{id}/complete`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseMissionOrderResponse`


### PUT `/api/v1/hrm/mission-orders/{id}/decline`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `DeclineMissionOrderRequest` (required)

**Responses:** 200 → `ApiResponseMissionOrderResponse`


### PUT `/api/v1/hrm/mission-orders/{id}/issue`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseMissionOrderResponse`


### PUT `/api/v1/hrm/mission-orders/{id}/start`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseMissionOrderResponse`



## notification-controller

### GET `/api/notifications/deliveries`
**Responses:** 200 → `NotificationDelivery[]`; 400 → `object`


### POST `/api/notifications/deliveries`
**Request body:** `application/json` → `SendNotificationRequest` (required)

**Responses:** 200 → `NotificationDelivery`; 400 → `object`


### POST `/api/notifications/preferences`
**Request body:** `application/json` → `SavePreferenceRequest` (required)

**Responses:** 200 → `NotificationPreference`; 400 → `object`


### GET `/api/notifications/preferences/users/{userId}`
**Parameters:**
- `userId` (path, string(uuid), required)

**Responses:** 200 → `NotificationPreference[]`; 400 → `object`


### GET `/api/notifications/providers`
**Responses:** 200 → `NotificationProvider[]`; 400 → `object`


### POST `/api/notifications/providers`
**Request body:** `application/json` → `SaveProviderRequest` (required)

**Responses:** 200 → `NotificationProvider`; 400 → `object`


### GET `/api/notifications/reminders`
**Responses:** 200 → `NotificationReminder[]`; 400 → `object`


### POST `/api/notifications/reminders`
**Request body:** `application/json` → `SaveReminderRequest` (required)

**Responses:** 200 → `NotificationReminder`; 400 → `object`


### GET `/api/notifications/templates`
**Responses:** 200 → `NotificationTemplate[]`; 400 → `object`


### POST `/api/notifications/templates`
**Request body:** `application/json` → `SaveTemplateRequest` (required)

**Responses:** 200 → `NotificationTemplate`; 400 → `object`



## observability-controller

### GET `/api/observability/outbox/events`
**Parameters:**
- `tenantId` (query, string(uuid), required)
- `status` (query, string, optional)
- `limit` (query, integer(int32), optional)

**Responses:** 200 → `ApiResponse`


### GET `/api/observability/outbox/summary`
**Parameters:**
- `tenantId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponse`


### GET `/api/observability/projections`
**Parameters:**
- `tenantId` (query, string(uuid), required)
- `domainType` (query, string, optional)
- `limit` (query, integer(int32), optional)

**Responses:** 200 → `ApiResponse`


### GET `/api/observability/projections/summary`
**Parameters:**
- `tenantId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponse`


### GET `/api/observability/runtime`
**Responses:** 200 → `ApiResponse`



## opening-hours-controller

### POST `/api/organizations/opening-hours`
**Request body:** `application/json` → `UpsertOpeningHoursRequest` (required)

**Responses:** 200 → `ApiResponseOpeningHoursResponse`


### GET `/api/organizations/opening-hours/{organizationId}/agencies/{agencyId}`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListOpeningHoursResponse`



## operation-template-controller

### GET `/api/accounting/operation-templates`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListOperationTemplateResponse`


### POST `/api/accounting/operation-templates`
**Request body:** `application/json` → `CreateOperationTemplateRequest` (required)

**Responses:** 200 → `ApiResponseOperationTemplateResponse`


### GET `/api/accounting/operation-templates/{templateId}`
**Parameters:**
- `templateId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseOperationTemplateResponse`


### POST `/api/accounting/operation-templates/{templateId}/generate`
**Parameters:**
- `templateId` (path, string(uuid), required)

**Request body:** `application/json` → `GenerateEntryFromTemplateRequest` (required)

**Responses:** 200 → `ApiResponseJournalEntryResponse`



## operational-excellence-controller

### POST `/api/administration/operational-excellence/documents/{documentLinkId}/approve`
**Parameters:**
- `documentLinkId` (path, string(uuid), required)

**Request body:** `application/json` → `ApproveDocumentRequest` (required)

**Responses:** 200 → `ApiResponseDocumentReviewResponse`


### POST `/api/administration/operational-excellence/organizations/{organizationId}/agencies/{agencyId}/commission-site`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)

**Request body:** `application/json` → `CommissionSiteRequest` (required)

**Responses:** 200 → `ApiResponseAgencyOperationalPilotageView`


### GET `/api/administration/operational-excellence/organizations/{organizationId}/agencies/{agencyId}/pilotage`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseAgencyOperationalPilotageView`


### GET `/api/administration/operational-excellence/organizations/{organizationId}/compliance`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseOperationalComplianceOverview`


### POST `/api/administration/operational-excellence/organizations/{organizationId}/inventory-campaigns/prepare`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Request body:** `application/json` → `PrepareInventoryCampaignRequest` (required)

**Responses:** 200 → `ApiResponseGeneralizedInventoryCampaignResponse`


### GET `/api/administration/operational-excellence/organizations/{organizationId}/pilotage`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseOrganizationOperationalPilotageView`


### GET `/api/administration/operational-excellence/organizations/{organizationId}/timeline`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `limit` (query, integer(int32), optional)

**Responses:** 200 → `ApiResponseListTimelineEntryView`


### POST `/api/administration/operational-excellence/resources/{resourceId}/commission`
**Parameters:**
- `resourceId` (path, string(uuid), required)

**Request body:** `application/json` → `CommissionAssetRequest` (required)

**Responses:** 200 → `ApiResponseAssetProfileResponse`


### POST `/api/administration/operational-excellence/resources/{resourceId}/retire`
**Parameters:**
- `resourceId` (path, string(uuid), required)

**Request body:** `application/json` → `RetireAssetRequest` (required)

**Responses:** 200 → `ApiResponseAssetProfileResponse`



## operational-policy-controller

### GET `/api/settings/organizations/{organizationId}/agencies/{agencyId}/operational-policy`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseOperationalPolicyResponse`


### PUT `/api/settings/organizations/{organizationId}/agencies/{agencyId}/operational-policy`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)

**Request body:** `application/json` → `UpsertOperationalPolicyRequest` (required)

**Responses:** 200 → `ApiResponseOperationalPolicyResponse`


### GET `/api/settings/organizations/{organizationId}/operational-policy`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseOperationalPolicyResponse`


### PUT `/api/settings/organizations/{organizationId}/operational-policy`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Request body:** `application/json` → `UpsertOperationalPolicyRequest` (required)

**Responses:** 200 → `ApiResponseOperationalPolicyResponse`



## operational-site-governance-controller

### GET `/api/organizations/{organizationId}/agencies/{agencyId}/operational-responsibilities`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)
- `physicalSpaceId` (query, string(uuid), optional)

**Responses:** 200 → `ApiResponseListOperationalResponsibilityResponse`


### POST `/api/organizations/{organizationId}/agencies/{agencyId}/operational-responsibilities`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)

**Request body:** `application/json` → `AssignOperationalResponsibilityRequest` (required)

**Responses:** 200 → `ApiResponseOperationalResponsibilityResponse`


### GET `/api/organizations/{organizationId}/agencies/{agencyId}/operational-site-profile`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseOperationalSiteProfileResponse`


### PUT `/api/organizations/{organizationId}/agencies/{agencyId}/operational-site-profile`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)

**Request body:** `application/json` → `UpsertOperationalSiteProfileRequest` (required)

**Responses:** 200 → `ApiResponseOperationalSiteProfileResponse`


### GET `/api/organizations/{organizationId}/agencies/{agencyId}/operational-site-readiness`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseOperationalSiteReadinessView`



## operational-workspace-controller

### GET `/api/organizations/{organizationId}/agencies/{agencyId}/generalized-inventory`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseGeneralizedInventoryView`


### GET `/api/organizations/{organizationId}/agencies/{agencyId}/operational-site`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseOperationalSiteView`


### GET `/api/organizations/{organizationId}/generalized-inventory`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseGeneralizedInventoryView`


### GET `/api/organizations/{organizationId}/service-workspaces/{workspaceCode}`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `workspaceCode` (path, string, required)

**Responses:** 200 → `ApiResponseServiceWorkspaceView`


### GET `/api/warehouses/{warehouseId}/generalized-inventory`
**Parameters:**
- `warehouseId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseGeneralizedInventoryView`


### GET `/api/warehouses/{warehouseId}/operational-site`
**Parameters:**
- `warehouseId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseOperationalSiteView`



## organization-address-book-controller

### GET `/api/agencies/{agencyId}/addresses`
**Parameters:**
- `agencyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListAddressResponse`


### POST `/api/agencies/{agencyId}/addresses`
**Parameters:**
- `agencyId` (path, string(uuid), required)

**Request body:** `application/json` → `NestedAddressRequest` (required)

**Responses:** 200 → `ApiResponseAddressResponse`


### DELETE `/api/agencies/{agencyId}/addresses/{addressId}`
**Parameters:**
- `agencyId` (path, string(uuid), required)
- `addressId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### GET `/api/agencies/{agencyId}/contacts`
**Parameters:**
- `agencyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListContactResponse`


### POST `/api/agencies/{agencyId}/contacts`
**Parameters:**
- `agencyId` (path, string(uuid), required)

**Request body:** `application/json` → `NestedContactRequest` (required)

**Responses:** 200 → `ApiResponseContactResponse`


### DELETE `/api/agencies/{agencyId}/contacts/{contactId}`
**Parameters:**
- `agencyId` (path, string(uuid), required)
- `contactId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### GET `/api/organizations/{organizationId}/addresses`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListAddressResponse`


### POST `/api/organizations/{organizationId}/addresses`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Request body:** `application/json` → `NestedAddressRequest` (required)

**Responses:** 200 → `ApiResponseAddressResponse`


### DELETE `/api/organizations/{organizationId}/addresses/{addressId}`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `addressId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### GET `/api/organizations/{organizationId}/contacts`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListContactResponse`


### POST `/api/organizations/{organizationId}/contacts`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Request body:** `application/json` → `NestedContactRequest` (required)

**Responses:** 200 → `ApiResponseContactResponse`


### DELETE `/api/organizations/{organizationId}/contacts/{contactId}`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `contactId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`



## organization-controller

### GET `/api/organizations`
**Responses:** 200 → `ApiResponseListOrganizationResponse`


### POST `/api/organizations`
**Request body:** `application/json` → `CreateOrganizationRequest` (required)

**Responses:** 200 → `ApiResponseOrganizationResponse`


### GET `/api/organizations/my`
**Responses:** 200 → `ApiResponseListOrganizationResponse`


### GET `/api/organizations/search`
**Parameters:**
- `q` (query, string, required)
- `organizationType` (query, string, optional)

**Responses:** 200 → `ApiResponseListOrganizationSearchResponse`


### GET `/api/organizations/{organizationId}`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseOrganizationResponse`


### PATCH `/api/organizations/{organizationId}`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateOrganizationRequest` (required)

**Responses:** 200 → `ApiResponseOrganizationResponse`


### POST `/api/organizations/{organizationId}/approve`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Request body:** `application/json` → `GovernanceActionRequest` (required)

**Responses:** 200 → `ApiResponseOrganizationResponse`


### POST `/api/organizations/{organizationId}/close`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Request body:** `application/json` → `GovernanceActionRequest` (required)

**Responses:** 200 → `ApiResponseOrganizationResponse`


### POST `/api/organizations/{organizationId}/reject`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Request body:** `application/json` → `GovernanceActionRequest` (required)

**Responses:** 200 → `ApiResponseOrganizationResponse`


### POST `/api/organizations/{organizationId}/reopen`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Request body:** `application/json` → `GovernanceActionRequest` (required)

**Responses:** 200 → `ApiResponseOrganizationResponse`


### POST `/api/organizations/{organizationId}/suspend`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Request body:** `application/json` → `GovernanceActionRequest` (required)

**Responses:** 200 → `ApiResponseOrganizationResponse`


### POST `/api/organizations/{organizationId}/transfer/{newOwnerId}`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `newOwnerId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseOrganizationResponse`



## organization-service-controller

### GET `/api/organizations/commercial-subscriptions/catalog`
**Responses:** 200 → `ApiResponseCommercialSubscriptionCatalogResponse`


### POST `/api/organizations/commercial-subscriptions/plans`
**Request body:** `application/json` → `SaveCommercialPlanRequest` (required)

**Responses:** 200 → `ApiResponseCommercialPlanCatalogResponse`


### DELETE `/api/organizations/commercial-subscriptions/plans/{planCode}`
**Parameters:**
- `planCode` (path, string, required)

**Responses:** 200 → `ApiResponseVoid`


### PATCH `/api/organizations/commercial-subscriptions/plans/{planCode}`
**Parameters:**
- `planCode` (path, string, required)

**Request body:** `application/json` → `SaveCommercialPlanRequest` (required)

**Responses:** 200 → `ApiResponseCommercialPlanCatalogResponse`


### GET `/api/organizations/services/catalog`
**Responses:** 200 → `ApiResponseListOrganizationServiceCatalogResponse`


### GET `/api/organizations/services/packs`
**Responses:** 200 → `ApiResponseListOrganizationServicePackResponse`


### POST `/api/organizations/{organizationId}/commercial-subscriptions`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Request body:** `application/json` → `ApplyCommercialSubscriptionRequest` (required)

**Responses:** 200 → `ApiResponseOrganizationCommercialSubscriptionResponse`


### GET `/api/organizations/{organizationId}/services`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseOrganizationServicesResponse`


### POST `/api/organizations/{organizationId}/services`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Request body:** `application/json` → `SubscribeOrganizationServiceRequest` (required)

**Responses:** 200 → `ApiResponseOrganizationServicesResponse`


### DELETE `/api/organizations/{organizationId}/services/{serviceCode}`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `serviceCode` (path, string, required)

**Responses:** 200 → `ApiResponseOrganizationServicesResponse`


### PATCH `/api/organizations/{organizationId}/services/{serviceCode}/quota`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `serviceCode` (path, string, required)

**Request body:** `application/json` → `UpdateOrganizationServiceQuotaRequest` (required)

**Responses:** 200 → `ApiResponseOrganizationServicesResponse`



## organization-structure-controller

### GET `/api/agencies/{agencyId}/affiliations`
**Parameters:**
- `agencyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListAgencyAffiliationResponse`


### POST `/api/agencies/{agencyId}/affiliations`
**Parameters:**
- `agencyId` (path, string(uuid), required)

**Request body:** `application/json` → `CreateAgencyAffiliationRequest` (required)

**Responses:** 200 → `ApiResponseAgencyAffiliationResponse`


### GET `/api/agencies/{agencyId}/domains`
**Parameters:**
- `agencyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListAgencyDomainResponse`


### POST `/api/agencies/{agencyId}/domains`
**Parameters:**
- `agencyId` (path, string(uuid), required)

**Request body:** `application/json` → `LinkAgencyDomainRequest` (required)

**Responses:** 200 → `ApiResponseAgencyDomainResponse`


### GET `/api/organizations/{organizationId}/activities`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListProposedActivityResponse`


### POST `/api/organizations/{organizationId}/activities`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Request body:** `application/json` → `CreateProposedActivityRequest` (required)

**Responses:** 200 → `ApiResponseProposedActivityResponse`


### GET `/api/organizations/{organizationId}/actors`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListOrganizationActorResponse`


### POST `/api/organizations/{organizationId}/actors`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Request body:** `application/json` → `LinkOrganizationActorRequest` (required)

**Responses:** 200 → `ApiResponseOrganizationActorResponse`


### GET `/api/organizations/{organizationId}/certifications`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListCertificationResponse`


### POST `/api/organizations/{organizationId}/certifications`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Request body:** `application/json` → `CreateCertificationRequest` (required)

**Responses:** 200 → `ApiResponseCertificationResponse`


### GET `/api/organizations/{organizationId}/domains`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListOrganizationDomainResponse`


### POST `/api/organizations/{organizationId}/domains`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Request body:** `application/json` → `LinkOrganizationDomainRequest` (required)

**Responses:** 200 → `ApiResponseOrganizationDomainResponse`



## pay-element-admin-controller

### GET `/api/v1/payroll/pay-elements`
**Parameters:**
- `countryCode` (query, string, required)

**Responses:** 200 → `ApiResponseListPayElementResponse`


### POST `/api/v1/payroll/pay-elements`
**Request body:** `application/json` → `CreatePayElementRequest` (required)

**Responses:** 200 → `ApiResponsePayElementResponse`


### DELETE `/api/v1/payroll/pay-elements/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponsePayElementResponse`


### GET `/api/v1/payroll/pay-elements/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponsePayElementResponse`


### PUT `/api/v1/payroll/pay-elements/{id}/activate`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponsePayElementResponse`



## pay-variable-controller

### GET `/api/v1/payroll/variables`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `period` (query, string, required)

**Responses:** 200 → `ApiResponseListPayVariableResponse`


### POST `/api/v1/payroll/variables`
**Request body:** `application/json` → `CapturePayVariableRequest` (required)

**Responses:** 200 → `ApiResponsePayVariableResponse`


### GET `/api/v1/payroll/variables/{employeeId}`
**Parameters:**
- `employeeId` (path, string(uuid), required)
- `period` (query, string, required)

**Responses:** 200 → `ApiResponsePayVariableResponse`



## payment-controller

### POST `/api/payments/wallets`
**Request body:** `application/json` → `CreateWalletRequest` (optional)

**Responses:** 200 → `ApiResponseWalletResponse`


### GET `/api/payments/wallets/owner/{ownerId}`
**Parameters:**
- `ownerId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseWalletResponse`


### GET `/api/payments/wallets/{walletId}`
**Parameters:**
- `walletId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseWalletResponse`


### GET `/api/payments/wallets/{walletId}/can-operate`
**Parameters:**
- `walletId` (path, string(uuid), required)
- `amount` (query, number, required)

**Responses:** 200 → `ApiResponseBoolean`


### POST `/api/payments/wallets/{walletId}/pay`
**Parameters:**
- `walletId` (path, string(uuid), required)

**Request body:** `application/json` → `TransactionRequest` (required)

**Responses:** 200 → `ApiResponseTransactionResponse`


### POST `/api/payments/wallets/{walletId}/recharge`
**Parameters:**
- `walletId` (path, string(uuid), required)

**Request body:** `application/json` → `TransactionRequest` (required)

**Responses:** 200 → `ApiResponseTransactionResponse`


### GET `/api/payments/wallets/{walletId}/transactions`
**Parameters:**
- `walletId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListTransactionResponse`



## payroll-document-controller

### GET `/api/v1/payroll/documents`
**Parameters:**
- `employeeId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListDocumentResponse`


### POST `/api/v1/payroll/documents/final-settlement`
**Parameters:**
- `settlementId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseDocumentResponse`


### POST `/api/v1/payroll/documents/payslip`
**Parameters:**
- `entryId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseDocumentResponse`


### POST `/api/v1/payroll/documents/work-certificate`
**Parameters:**
- `employeeId` (query, string(uuid), required)
- `position` (query, string, optional)

**Responses:** 200 → `ApiResponseDocumentResponse`


### GET `/api/v1/payroll/documents/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseDocumentResponse`


### GET `/api/v1/payroll/documents/{id}/verify`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseDocumentVerification`



## payroll-employee-controller

### GET `/api/v1/payroll/employees`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListPayrollEmployeeResponse`


### POST `/api/v1/payroll/employees`
**Request body:** `application/json` → `UpsertPayrollEmployeeRequest` (required)

**Responses:** 200 → `ApiResponsePayrollEmployeeResponse`


### GET `/api/v1/payroll/employees/data-source`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseDataSourceResponse`


### PUT `/api/v1/payroll/employees/data-source`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Request body:** `application/json` → `DataSourceRequest` (required)

**Responses:** 200 → `ApiResponseDataSourceResponse`


### POST `/api/v1/payroll/employees/import`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Request body:** `application/json` → `CsvImportRequest` (required)

**Responses:** 200 → `ApiResponseCsvImportReport`


### GET `/api/v1/payroll/employees/template`
**Responses:** 200 → `ApiResponseCsvTemplateResponse`


### GET `/api/v1/payroll/employees/{payrollEmployeeId}`
**Parameters:**
- `payrollEmployeeId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponsePayrollEmployeeResponse`


### PUT `/api/v1/payroll/employees/{payrollEmployeeId}`
**Parameters:**
- `payrollEmployeeId` (path, string(uuid), required)

**Request body:** `application/json` → `UpsertPayrollEmployeeRequest` (required)

**Responses:** 200 → `ApiResponsePayrollEmployeeResponse`


### PUT `/api/v1/payroll/employees/{payrollEmployeeId}/deactivate`
**Parameters:**
- `payrollEmployeeId` (path, string(uuid), required)

**Request body:** `application/json` → `DeactivateRequest` (optional)

**Responses:** 200 → `ApiResponsePayrollEmployeeResponse`



## payroll-onboarding-controller

### GET `/api/v1/payroll/onboarding/manifest`
**Responses:** 200 → `ApiResponsePayrollOnboardingManifest`



## payroll-run-controller

### POST `/api/v1/payroll/entries/{payrollEntryId}/payment-callback`
**Parameters:**
- `payrollEntryId` (path, string(uuid), required)
- `status` (query, string, required)

**Responses:** 200 → `ApiResponseVoid`


### GET `/api/v1/payroll/entries/{payrollEntryId}/payslip`
**Parameters:**
- `payrollEntryId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListPayslipLineResponse`


### GET `/api/v1/payroll/runs`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListPayrollRunResponse`


### POST `/api/v1/payroll/runs`
**Request body:** `application/json` → `RunPayrollRequest` (required)

**Responses:** 200 → `ApiResponsePayrollRunResponse`


### GET `/api/v1/payroll/runs/{payrollRunId}`
**Parameters:**
- `payrollRunId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponsePayrollRunResponse`


### PUT `/api/v1/payroll/runs/{payrollRunId}/approve`
**Parameters:**
- `payrollRunId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponsePayrollRunResponse`


### PUT `/api/v1/payroll/runs/{payrollRunId}/close`
**Parameters:**
- `payrollRunId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponsePayrollRunResponse`


### GET `/api/v1/payroll/runs/{payrollRunId}/entries`
**Parameters:**
- `payrollRunId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListPayrollEntryResponse`


### PUT `/api/v1/payroll/runs/{payrollRunId}/initiate-payment`
**Parameters:**
- `payrollRunId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponsePayrollRunResponse`


### PUT `/api/v1/payroll/runs/{payrollRunId}/reject`
**Parameters:**
- `payrollRunId` (path, string(uuid), required)

**Request body:** `application/json` → `RejectRunRequest` (required)

**Responses:** 200 → `ApiResponsePayrollRunResponse`


### PUT `/api/v1/payroll/runs/{payrollRunId}/validate`
**Parameters:**
- `payrollRunId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponsePayrollRunResponse`



## payslip-self-service-controller

### GET `/api/v1/payroll/my-entries`
**Responses:** 200 → `ApiResponseListMyPayslipResponse`


### GET `/api/v1/payroll/my-entries/{entryId}/payslip`
**Parameters:**
- `entryId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListPayslipLineResponse`



## physical-space-controller

### GET `/api/organizations/{organizationId}/agencies/{agencyId}/physical-spaces`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListPhysicalSpaceResponse`


### POST `/api/organizations/{organizationId}/agencies/{agencyId}/physical-spaces`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)

**Request body:** `application/json` → `CreatePhysicalSpaceRequest` (required)

**Responses:** 200 → `ApiResponsePhysicalSpaceResponse`


### GET `/api/organizations/{organizationId}/agencies/{agencyId}/physical-spaces/tree`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListPhysicalSpaceResponse`



## plan-controller

### GET `/api/plans`
**Responses:** 200 → `ApiResponseListPlanResponse`


### GET `/api/plans/subscriptions`
**Responses:** 200 → `ApiResponseListSubscriptionResponse`


### DELETE `/api/plans/{code}`
**Parameters:**
- `code` (path, string, required)

**Responses:** 200 → `ApiResponseVoid`


### GET `/api/plans/{code}`
**Parameters:**
- `code` (path, string, required)

**Responses:** 200 → `ApiResponsePlanResponse`


### PUT `/api/plans/{code}`
**Parameters:**
- `code` (path, string, required)

**Request body:** `application/json` → `SavePlanRequest` (required)

**Responses:** 200 → `ApiResponsePlanResponse`


### POST `/api/plans/{code}/purchase`
**Parameters:**
- `code` (path, string, required)

**Request body:** `application/json` → `PurchaseRequest` (optional)

**Responses:** 200 → `ApiResponseSubscriptionResponse`



## platform-authorization-controller

### POST `/api/client-applications/me/authorize`
**Parameters:**
- `service` (query, string, required)

**Responses:** 200 → `ApiResponseMapStringObject`



## platform-service-controller

### GET `/api/platform-services`
**Responses:** 200 → `ApiResponseListCatalogServiceView`


### POST `/api/platform-services`
**Request body:** `application/json` → `RegisterExternalServiceRequest` (required)

**Responses:** 200 → `ApiResponseCatalogServiceView`


### DELETE `/api/platform-services/{code}`
**Parameters:**
- `code` (path, string, required)

**Responses:** 200 → `ApiResponseVoid`



## point-of-interest-controller

### POST `/api/organizations/points-of-interest`
**Request body:** `application/json` → `CreatePointOfInterestRequest` (required)

**Responses:** 200 → `ApiResponsePointOfInterestResponse`


### GET `/api/organizations/points-of-interest/{organizationId}/agencies/{agencyId}`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListPointOfInterestResponse`



## product-catalog-controller

### GET `/api/product-categories`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListProductCategoryResponse`


### POST `/api/product-categories`
**Request body:** `application/json` → `CreateProductCategoryRequest` (required)

**Responses:** 200 → `ApiResponseProductCategoryResponse`


### DELETE `/api/product-categories/{categoryId}`
**Parameters:**
- `categoryId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### PATCH `/api/product-categories/{categoryId}`
**Parameters:**
- `categoryId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateProductCategoryRequest` (required)

**Responses:** 200 → `ApiResponseProductCategoryResponse`


### GET `/api/products/{productId}/prices`
**Parameters:**
- `productId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListProductPriceResponse`


### POST `/api/products/{productId}/prices`
**Parameters:**
- `productId` (path, string(uuid), required)

**Request body:** `application/json` → `DefineProductPriceRequest` (required)

**Responses:** 200 → `ApiResponseProductPriceResponse`


### GET `/api/products/{productId}/prices/effective`
**Parameters:**
- `productId` (path, string(uuid), required)
- `priceType` (query, string, required)
- `at` (query, string(date-time), optional)

**Responses:** 200 → `ApiResponseProductPriceResponse`



## product-controller

### GET `/api/products`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `familyCode` (query, string, optional)
- `status` (query, string, optional)

**Responses:** 200 → `ApiResponseListProductResponse`


### POST `/api/products`
**Request body:** `application/json` → `CreateProductRequest` (required)

**Responses:** 200 → `ApiResponseProductResponse`


### GET `/api/products/search`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `q` (query, string, required)
- `familyCode` (query, string, optional)
- `status` (query, string, optional)

**Responses:** 200 → `ApiResponseListProductSearchResponse`


### DELETE `/api/products/{productId}`
**Parameters:**
- `productId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### GET `/api/products/{productId}`
**Parameters:**
- `productId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseProductResponse`


### PATCH `/api/products/{productId}`
**Parameters:**
- `productId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateProductRequest` (required)

**Responses:** 200 → `ApiResponseProductResponse`



## product-structure-controller

### GET `/api/media-assets`
**Parameters:**
- `targetType` (query, string, required)
- `targetId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListMediaAssetResponse`


### POST `/api/media-assets`
**Request body:** `application/json` → `CreateMediaAssetRequest` (required)

**Responses:** 200 → `ApiResponseMediaAssetResponse`


### GET `/api/product-categories/{categoryId}/translations`
**Parameters:**
- `categoryId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListCategoryI18nResponse`


### POST `/api/product-categories/{categoryId}/translations`
**Parameters:**
- `categoryId` (path, string(uuid), required)

**Request body:** `application/json` → `UpsertCategoryTranslationRequest` (required)

**Responses:** 200 → `ApiResponseCategoryI18nResponse`


### GET `/api/products/{productId}/batches`
**Parameters:**
- `productId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListBatchResponse`


### POST `/api/products/{productId}/batches`
**Parameters:**
- `productId` (path, string(uuid), required)

**Request body:** `application/json` → `CreateBatchRequest` (required)

**Responses:** 200 → `ApiResponseBatchResponse`


### GET `/api/products/{productId}/spec`
**Parameters:**
- `productId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseProductSpecResponse`


### PUT `/api/products/{productId}/spec`
**Parameters:**
- `productId` (path, string(uuid), required)

**Request body:** `application/json` → `UpsertProductSpecRequest` (required)

**Responses:** 200 → `ApiResponseProductSpecResponse`


### GET `/api/products/{productId}/variants`
**Parameters:**
- `productId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListVariantResponse`


### POST `/api/products/{productId}/variants`
**Parameters:**
- `productId` (path, string(uuid), required)

**Request body:** `application/json` → `CreateVariantRequest` (required)

**Responses:** 200 → `ApiResponseVariantResponse`


### GET `/api/variants/{variantId}/attributes`
**Parameters:**
- `variantId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListVariantAttributeResponse`


### POST `/api/variants/{variantId}/attributes`
**Parameters:**
- `variantId` (path, string(uuid), required)

**Request body:** `application/json` → `AddVariantAttributeRequest` (required)

**Responses:** 200 → `ApiResponseVariantAttributeResponse`


### GET `/api/variants/{variantId}/prices`
**Parameters:**
- `variantId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListVariantPriceResponse`


### POST `/api/variants/{variantId}/prices`
**Parameters:**
- `variantId` (path, string(uuid), required)

**Request body:** `application/json` → `DefineVariantPriceRequest` (required)

**Responses:** 200 → `ApiResponseVariantPriceResponse`


### GET `/api/variants/{variantId}/prices/effective`
**Parameters:**
- `variantId` (path, string(uuid), required)
- `priceType` (query, string, required)
- `at` (query, string(date-time), optional)

**Responses:** 200 → `ApiResponseVariantPriceResponse`



## product-transformation-controller

### GET `/api/inventory/transformations`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `agencyId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListProductTransformationResponse`


### POST `/api/inventory/transformations`
**Request body:** `application/json` → `RecordTransformationRequest` (required)

**Responses:** 200 → `ApiResponseProductTransformationResponse`


### POST `/api/inventory/transformations/{transformationId}/validate`
**Parameters:**
- `transformationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseProductTransformationResponse`



## prospect-controller

### GET `/api/prospects`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `prospect` (query, boolean, optional)

**Responses:** 200 → `ApiResponseListThirdPartyResponse`


### POST `/api/prospects`
**Request body:** `application/json` → `CreateCommercialThirdPartyRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### GET `/api/prospects/by-accounting-account/{accountingAccount}`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `accountingAccount` (path, string, required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### GET `/api/prospects/by-bank-account/{bankAccountNumber}`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `bankAccountNumber` (path, string, required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### GET `/api/prospects/search`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `q` (query, string, required)
- `segment` (query, string, optional)
- `minimumQualificationScore` (query, integer(int32), optional)
- `active` (query, boolean, optional)
- `followUpStatus` (query, string, optional)
- `page` (query, integer(int32), optional)
- `size` (query, integer(int32), optional)

**Responses:** 200 → `ApiResponseListThirdPartySearchResponse`


### GET `/api/prospects/statistics`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyStatisticsResponse`


### GET `/api/prospects/statistics/conversions`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseLong`


### GET `/api/prospects/without-accounting-account`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListThirdPartyResponse`


### GET `/api/prospects/without-bank-account`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListThirdPartyResponse`


### DELETE `/api/prospects/{thirdPartyId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### GET `/api/prospects/{thirdPartyId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/prospects/{thirdPartyId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateThirdPartyRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/prospects/{thirdPartyId}/accounting-account`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `string` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/prospects/{thirdPartyId}/activate`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/prospects/{thirdPartyId}/bank-account`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `string` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### GET `/api/prospects/{thirdPartyId}/bank-accounts`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListThirdPartyBankAccountResponse`


### POST `/api/prospects/{thirdPartyId}/bank-accounts`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `ThirdPartyBankAccountRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyBankAccountResponse`


### DELETE `/api/prospects/{thirdPartyId}/bank-accounts/{bankAccountId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)
- `bankAccountId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### PATCH `/api/prospects/{thirdPartyId}/bank-accounts/{bankAccountId}/set-primary`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)
- `bankAccountId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### POST `/api/prospects/{thirdPartyId}/convert`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/prospects/{thirdPartyId}/deactivate`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/prospects/{thirdPartyId}/follow-up/complete`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `ThirdPartyFollowUpRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/prospects/{thirdPartyId}/follow-up/schedule`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `ThirdPartyFollowUpRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/prospects/{thirdPartyId}/qualification`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `ThirdPartyQualificationRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### POST `/api/prospects/{thirdPartyId}/resend-credentials`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### POST `/api/prospects/{thirdPartyId}/score/recompute`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`



## public-organization-branding-controller

### GET `/api/public/organizations/{organizationId}/branding`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `tenantId` (query, string(uuid), required)

**Responses:** 200 → `PublicOrganizationBrandingResponse`



## purchase-order-controller

### GET `/api/spare/purchase-orders`
**Responses:** 200 → `ApiResponseListPurchaseOrderResponse`


### POST `/api/spare/purchase-orders`
**Request body:** `application/json` → `CreateRequest` (required)

**Responses:** 200 → `ApiResponsePurchaseOrderResponse`


### DELETE `/api/spare/purchase-orders/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200


### GET `/api/spare/purchase-orders/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponsePurchaseOrderResponse`


### PATCH `/api/spare/purchase-orders/{id}/status`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateStatusRequest` (required)

**Responses:** 200 → `ApiResponsePurchaseOrderResponse`



## receipt-controller

### GET `/api/spare/receipts`
**Parameters:**
- `poId` (query, string(uuid), optional)

**Responses:** 200 → `ApiResponseListReceiptResponse`


### POST `/api/spare/receipts`
**Request body:** `application/json` → `CreateRequest` (required)

**Responses:** 200 → `ApiResponseReceiptResponse`


### GET `/api/spare/receipts/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseReceiptResponse`


### PATCH `/api/spare/receipts/{id}/post-to-stock`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseReceiptResponse`



## reconciliation-match-controller

### POST `/api/banking/reconciliation/auto-match`
**Parameters:**
- `statementId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseAutoMatchResult`


### POST `/api/banking/reconciliation/manual-match`
**Request body:** `application/json` → `ManualMatchRequest` (required)

**Responses:** 200 → `ApiResponseReconciliationMatchResponse`


### POST `/api/banking/reconciliation/match-with-new`
**Request body:** `application/json` → `MatchWithNewTransactionRequest` (required)

**Responses:** 200 → `ApiResponseReconciliationMatchResponse`


### GET `/api/banking/reconciliation/suggestions`
**Parameters:**
- `statementLineId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListReconciliationSuggestionResponse`


### GET `/api/banking/reconciliation/summary`
**Parameters:**
- `statementId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseReconciliationSummary`


### POST `/api/banking/reconciliation/unmatch/{matchId}`
**Parameters:**
- `matchId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseReconciliationMatchResponse`


### POST `/api/treasury/reconciliation/auto-match`
**Parameters:**
- `statementId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseAutoMatchResult`


### POST `/api/treasury/reconciliation/manual-match`
**Request body:** `application/json` → `ManualMatchRequest` (required)

**Responses:** 200 → `ApiResponseReconciliationMatchResponse`


### POST `/api/treasury/reconciliation/match-with-new`
**Request body:** `application/json` → `MatchWithNewTransactionRequest` (required)

**Responses:** 200 → `ApiResponseReconciliationMatchResponse`


### GET `/api/treasury/reconciliation/suggestions`
**Parameters:**
- `statementLineId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListReconciliationSuggestionResponse`


### GET `/api/treasury/reconciliation/summary`
**Parameters:**
- `statementId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseReconciliationSummary`


### POST `/api/treasury/reconciliation/unmatch/{matchId}`
**Parameters:**
- `matchId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseReconciliationMatchResponse`



## recruitment-controller

### POST `/api/v1/hrm/applications`
**Request body:** `application/json` → `CreateApplicationRequest` (required)

**Responses:** 200 → `ApiResponseApplicationResponse`


### GET `/api/v1/hrm/applications/{applicationId}/interviews`
**Parameters:**
- `applicationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListInterviewResponse`


### GET `/api/v1/hrm/applications/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseApplicationResponse`


### POST `/api/v1/hrm/applications/{id}/convert-to-employee`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `ConvertApplicationRequest` (required)

**Responses:** 200 → `ApiResponseEmployeeResponse`


### PUT `/api/v1/hrm/applications/{id}/hire`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseApplicationResponse`


### PUT `/api/v1/hrm/applications/{id}/interview`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseApplicationResponse`


### PUT `/api/v1/hrm/applications/{id}/offer`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseApplicationResponse`


### PUT `/api/v1/hrm/applications/{id}/reject`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseApplicationResponse`


### PUT `/api/v1/hrm/applications/{id}/shortlist`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseApplicationResponse`


### POST `/api/v1/hrm/interviews`
**Request body:** `application/json` → `ScheduleInterviewRequest` (required)

**Responses:** 200 → `ApiResponseInterviewResponse`


### PUT `/api/v1/hrm/interviews/{id}/complete`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `CompleteInterviewRequest` (required)

**Responses:** 200 → `ApiResponseInterviewResponse`


### GET `/api/v1/hrm/job-offers`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListJobOfferResponse`


### POST `/api/v1/hrm/job-offers`
**Request body:** `application/json` → `CreateJobOfferRequest` (required)

**Responses:** 200 → `ApiResponseJobOfferResponse`


### GET `/api/v1/hrm/job-offers/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseJobOfferResponse`


### PUT `/api/v1/hrm/job-offers/{id}/close`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseJobOfferResponse`


### PUT `/api/v1/hrm/job-offers/{id}/publish`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseJobOfferResponse`


### GET `/api/v1/hrm/job-offers/{jobOfferId}/applications`
**Parameters:**
- `jobOfferId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListApplicationResponse`


### POST `/api/v1/hrm/onboarding-tasks`
**Request body:** `application/json` → `CreateOnboardingTaskRequest` (required)

**Responses:** 200 → `ApiResponseOnboardingTaskResponse`


### GET `/api/v1/hrm/onboarding-tasks/employee/{employeeId}`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListOnboardingTaskResponse`


### PUT `/api/v1/hrm/onboarding-tasks/{id}/complete`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseOnboardingTaskResponse`


### PUT `/api/v1/hrm/onboarding-tasks/{id}/start`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseOnboardingTaskResponse`



## resource-address-book-controller

### GET `/api/resources/{resourceId}/addresses`
**Parameters:**
- `resourceId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListAddressResponse`


### POST `/api/resources/{resourceId}/addresses`
**Parameters:**
- `resourceId` (path, string(uuid), required)

**Request body:** `application/json` → `NestedAddressRequest` (required)

**Responses:** 200 → `ApiResponseAddressResponse`


### DELETE `/api/resources/{resourceId}/addresses/{addressId}`
**Parameters:**
- `resourceId` (path, string(uuid), required)
- `addressId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### GET `/api/resources/{resourceId}/contacts`
**Parameters:**
- `resourceId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListContactResponse`


### POST `/api/resources/{resourceId}/contacts`
**Parameters:**
- `resourceId` (path, string(uuid), required)

**Request body:** `application/json` → `NestedContactRequest` (required)

**Responses:** 200 → `ApiResponseContactResponse`


### DELETE `/api/resources/{resourceId}/contacts/{contactId}`
**Parameters:**
- `resourceId` (path, string(uuid), required)
- `contactId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`



## resource-controller

### GET `/api/resources`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `agencyId` (query, string(uuid), optional)
- `category` (query, string, optional)
- `status` (query, string, optional)

**Responses:** 200 → `ApiResponseListMaterialResourceResponse`


### POST `/api/resources`
**Request body:** `application/json` → `RegisterMaterialResourceRequest` (required)

**Responses:** 200 → `ApiResponseMaterialResourceResponse`


### GET `/api/resources/search`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `q` (query, string, required)
- `agencyId` (query, string(uuid), optional)
- `category` (query, string, optional)
- `status` (query, string, optional)

**Responses:** 200 → `ApiResponseListMaterialResourceSearchResponse`


### GET `/api/resources/{resourceId}`
**Parameters:**
- `resourceId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseMaterialResourceResponse`


### GET `/api/resources/{resourceId}/assignments`
**Parameters:**
- `resourceId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListResourceAssignmentResponse`


### POST `/api/resources/{resourceId}/assignments`
**Parameters:**
- `resourceId` (path, string(uuid), required)

**Request body:** `application/json` → `AssignMaterialResourceRequest` (required)

**Responses:** 200 → `ApiResponseMaterialResourceResponse`


### POST `/api/resources/{resourceId}/dispose`
**Parameters:**
- `resourceId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseMaterialResourceResponse`


### GET `/api/resources/{resourceId}/location-observations`
**Parameters:**
- `resourceId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListResourceLocationObservationResponse`


### POST `/api/resources/{resourceId}/location-observations`
**Parameters:**
- `resourceId` (path, string(uuid), required)

**Request body:** `application/json` → `RecordLocationObservationRequest` (required)

**Responses:** 200 → `ApiResponseResourceLocationObservationResponse`


### GET `/api/resources/{resourceId}/maintenance`
**Parameters:**
- `resourceId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListMaintenanceRecordResponse`


### POST `/api/resources/{resourceId}/maintenance`
**Parameters:**
- `resourceId` (path, string(uuid), required)

**Request body:** `application/json` → `RecordMaintenanceRequest` (required)

**Responses:** 200 → `ApiResponseMaterialResourceResponse`


### GET `/api/resources/{resourceId}/network-observations`
**Parameters:**
- `resourceId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListResourceNetworkObservationResponse`


### POST `/api/resources/{resourceId}/network-observations`
**Parameters:**
- `resourceId` (path, string(uuid), required)

**Request body:** `application/json` → `RecordNetworkObservationRequest` (required)

**Responses:** 200 → `ApiResponseResourceNetworkObservationResponse`


### GET `/api/resources/{resourceId}/reservations`
**Parameters:**
- `resourceId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListResourceReservationResponse`


### POST `/api/resources/{resourceId}/reservations`
**Parameters:**
- `resourceId` (path, string(uuid), required)

**Request body:** `application/json` → `ReserveMaterialResourceRequest` (required)

**Responses:** 200 → `ApiResponseMaterialResourceResponse`


### POST `/api/resources/{resourceId}/reservations/{reservationId}/release`
**Parameters:**
- `resourceId` (path, string(uuid), required)
- `reservationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseMaterialResourceResponse`


### POST `/api/resources/{resourceId}/unassign`
**Parameters:**
- `resourceId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseMaterialResourceResponse`



## resource-target-controller

### GET `/api/actors/{actorId}/resources/assignments`
**Parameters:**
- `actorId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListResourceAssignmentResponse`


### GET `/api/actors/{actorId}/resources/reservations`
**Parameters:**
- `actorId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListResourceReservationResponse`


### GET `/api/agencies/{agencyId}/resources/assignments`
**Parameters:**
- `agencyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListResourceAssignmentResponse`


### GET `/api/agencies/{agencyId}/resources/reservations`
**Parameters:**
- `agencyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListResourceReservationResponse`


### GET `/api/organizations/{organizationId}/resources/assignments`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListResourceAssignmentResponse`


### GET `/api/organizations/{organizationId}/resources/reservations`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListResourceReservationResponse`


### GET `/api/physical-spaces/{spaceId}/resources/assignments`
**Parameters:**
- `spaceId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListResourceAssignmentResponse`


### GET `/api/physical-spaces/{spaceId}/resources/reservations`
**Parameters:**
- `spaceId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListResourceReservationResponse`



## retroactive-controller

### GET `/api/v1/payroll/retroactive`
**Parameters:**
- `employeeId` (query, string(uuid), optional)
- `organizationId` (query, string(uuid), optional)

**Responses:** 200 → `ApiResponseListRetroactiveResponse`


### POST `/api/v1/payroll/retroactive`
**Request body:** `application/json` → `CalculateRequest` (required)

**Responses:** 200 → `ApiResponseRetroactiveResponse`


### DELETE `/api/v1/payroll/retroactive/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseRetroactiveResponse`


### GET `/api/v1/payroll/retroactive/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseRetroactiveResponse`


### PUT `/api/v1/payroll/retroactive/{id}/apply`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseRetroactiveResponse`



## review-controller

### GET `/api/v1/hrm/reviews`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `periode` (query, string, required)

**Responses:** 200 → `ApiResponseListReviewResponse`


### POST `/api/v1/hrm/reviews`
**Request body:** `application/json` → `CreateReviewRequest` (required)

**Responses:** 200 → `ApiResponseReviewResponse`


### GET `/api/v1/hrm/reviews/employee/{employeeId}`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListReviewResponse`


### PUT `/api/v1/hrm/reviews/objectives/{objectiveId}/evaluate`
**Parameters:**
- `objectiveId` (path, string(uuid), required)

**Request body:** `application/json` → `EvaluateObjectiveRequest` (required)

**Responses:** 200 → `ApiResponseObjectiveResponse`


### GET `/api/v1/hrm/reviews/{reviewId}`
**Parameters:**
- `reviewId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseReviewResponse`


### PUT `/api/v1/hrm/reviews/{reviewId}/acknowledge`
**Parameters:**
- `reviewId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseReviewResponse`


### PUT `/api/v1/hrm/reviews/{reviewId}/finalize`
**Parameters:**
- `reviewId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseReviewResponse`


### GET `/api/v1/hrm/reviews/{reviewId}/objectives`
**Parameters:**
- `reviewId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListObjectiveResponse`


### POST `/api/v1/hrm/reviews/{reviewId}/objectives`
**Parameters:**
- `reviewId` (path, string(uuid), required)

**Request body:** `application/json` → `AddObjectiveRequest` (required)

**Responses:** 200 → `ApiResponseObjectiveResponse`


### PUT `/api/v1/hrm/reviews/{reviewId}/submit`
**Parameters:**
- `reviewId` (path, string(uuid), required)

**Request body:** `application/json` → `SubmitReviewRequest` (required)

**Responses:** 200 → `ApiResponseReviewResponse`



## review-self-service-controller

### PUT `/api/v1/hrm/reviews/me/{reviewId}/acknowledge`
**Parameters:**
- `reviewId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseReviewResponse`



## rh-kpi-controller

### GET `/api/v1/hrm/kpi`
**Parameters:**
- `orgId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListRhKpiSnapshotResponse`


### POST `/api/v1/hrm/kpi`
**Request body:** `application/json` → `CreateRhKpiSnapshotRequest` (required)

**Responses:** 200 → `ApiResponseRhKpiSnapshotResponse`


### GET `/api/v1/hrm/kpi/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseRhKpiSnapshotResponse`



## role-controller

### GET `/api/roles`
**Responses:** 200 → `RoleResponse[]`


### POST `/api/roles`
**Request body:** `application/json` → `CreateRoleRequest` (required)

**Responses:** 200 → `ApiResponseRoleResponse`


### GET `/api/roles/assignments`
**Parameters:**
- `userId` (query, string(uuid), required)

**Responses:** 200 → `UserRoleAssignmentResponse[]`


### POST `/api/roles/assignments`
**Request body:** `application/json` → `AssignRoleToUserRequest` (required)

**Responses:** 200 → `ApiResponseUserRoleAssignmentResponse`


### DELETE `/api/roles/assignments/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 204


### DELETE `/api/roles/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 204


### GET `/api/roles/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `RoleResponse`



## sales-agent-controller

### GET `/api/sales-agents`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `prospect` (query, boolean, optional)

**Responses:** 200 → `ApiResponseListThirdPartyResponse`


### POST `/api/sales-agents`
**Request body:** `application/json` → `CreateCommercialThirdPartyRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### GET `/api/sales-agents/by-accounting-account/{accountingAccount}`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `accountingAccount` (path, string, required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### GET `/api/sales-agents/by-bank-account/{bankAccountNumber}`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `bankAccountNumber` (path, string, required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### GET `/api/sales-agents/search`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `q` (query, string, required)
- `segment` (query, string, optional)
- `minimumQualificationScore` (query, integer(int32), optional)
- `active` (query, boolean, optional)
- `followUpStatus` (query, string, optional)
- `page` (query, integer(int32), optional)
- `size` (query, integer(int32), optional)

**Responses:** 200 → `ApiResponseListThirdPartySearchResponse`


### GET `/api/sales-agents/statistics`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyStatisticsResponse`


### GET `/api/sales-agents/without-accounting-account`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListThirdPartyResponse`


### GET `/api/sales-agents/without-bank-account`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListThirdPartyResponse`


### DELETE `/api/sales-agents/{thirdPartyId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### GET `/api/sales-agents/{thirdPartyId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/sales-agents/{thirdPartyId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateThirdPartyRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/sales-agents/{thirdPartyId}/accounting-account`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `string` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/sales-agents/{thirdPartyId}/activate`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/sales-agents/{thirdPartyId}/bank-account`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `string` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### GET `/api/sales-agents/{thirdPartyId}/bank-accounts`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListThirdPartyBankAccountResponse`


### POST `/api/sales-agents/{thirdPartyId}/bank-accounts`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `ThirdPartyBankAccountRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyBankAccountResponse`


### DELETE `/api/sales-agents/{thirdPartyId}/bank-accounts/{bankAccountId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)
- `bankAccountId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### PATCH `/api/sales-agents/{thirdPartyId}/bank-accounts/{bankAccountId}/set-primary`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)
- `bankAccountId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### PATCH `/api/sales-agents/{thirdPartyId}/deactivate`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/sales-agents/{thirdPartyId}/follow-up/complete`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `ThirdPartyFollowUpRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/sales-agents/{thirdPartyId}/follow-up/schedule`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `ThirdPartyFollowUpRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/sales-agents/{thirdPartyId}/qualification`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `ThirdPartyQualificationRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### POST `/api/sales-agents/{thirdPartyId}/resend-credentials`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### POST `/api/sales-agents/{thirdPartyId}/score/recompute`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`



## sales-controller

### GET `/api/sales/orders`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListSalesOrderResponse`


### POST `/api/sales/orders`
**Request body:** `application/json` → `CreateSalesOrderRequest` (required)

**Responses:** 200 → `ApiResponseSalesOrderResponse`


### DELETE `/api/sales/orders/{orderId}`
**Parameters:**
- `orderId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### GET `/api/sales/orders/{orderId}`
**Parameters:**
- `orderId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseSalesOrderResponse`


### PATCH `/api/sales/orders/{orderId}`
**Parameters:**
- `orderId` (path, string(uuid), required)

**Request body:** `application/json` → `CreateSalesOrderRequest` (required)

**Responses:** 200 → `ApiResponseSalesOrderResponse`


### POST `/api/sales/orders/{orderId}/cancel`
**Parameters:**
- `orderId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseSalesOrderResponse`


### POST `/api/sales/orders/{orderId}/confirm`
**Parameters:**
- `orderId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseSalesOrderResponse`



## scoped-resource-controller

### GET `/api/organizations/{organizationId}/agencies/{agencyId}/resources`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)
- `category` (query, string, optional)
- `status` (query, string, optional)

**Responses:** 200 → `ApiResponseListMaterialResourceResponse`


### POST `/api/organizations/{organizationId}/agencies/{agencyId}/resources`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)

**Request body:** `application/json` → `AgencyScopedResourceRequest` (required)

**Responses:** 200 → `ApiResponseMaterialResourceResponse`


### GET `/api/organizations/{organizationId}/agencies/{agencyId}/resources/search`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `agencyId` (path, string(uuid), required)
- `q` (query, string, required)
- `category` (query, string, optional)
- `status` (query, string, optional)

**Responses:** 200 → `ApiResponseListMaterialResourceSearchResponse`


### GET `/api/organizations/{organizationId}/resources`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `category` (query, string, optional)
- `status` (query, string, optional)

**Responses:** 200 → `ApiResponseListMaterialResourceResponse`


### POST `/api/organizations/{organizationId}/resources`
**Parameters:**
- `organizationId` (path, string(uuid), required)

**Request body:** `application/json` → `OrganizationScopedResourceRequest` (required)

**Responses:** 200 → `ApiResponseMaterialResourceResponse`


### GET `/api/organizations/{organizationId}/resources/search`
**Parameters:**
- `organizationId` (path, string(uuid), required)
- `q` (query, string, required)
- `category` (query, string, optional)
- `status` (query, string, optional)

**Responses:** 200 → `ApiResponseListMaterialResourceSearchResponse`


### GET `/api/warehouses/{warehouseId}/resources`
**Parameters:**
- `warehouseId` (path, string(uuid), required)
- `category` (query, string, optional)
- `status` (query, string, optional)

**Responses:** 200 → `ApiResponseListMaterialResourceResponse`


### POST `/api/warehouses/{warehouseId}/resources`
**Parameters:**
- `warehouseId` (path, string(uuid), required)

**Request body:** `application/json` → `WarehouseScopedResourceRequest` (required)

**Responses:** 200 → `ApiResponseMaterialResourceResponse`


### GET `/api/warehouses/{warehouseId}/resources/search`
**Parameters:**
- `warehouseId` (path, string(uuid), required)
- `q` (query, string, required)
- `category` (query, string, optional)
- `status` (query, string, optional)

**Responses:** 200 → `ApiResponseListMaterialResourceSearchResponse`



## session-tokens-controller

### POST `/api/auth/logout`
**Parameters:**
- `Authorization` (header, string, optional)

**Request body:** `application/json` → `RefreshTokenRequest` (optional)

**Responses:** 200 → `ApiResponseVoid`


### POST `/api/auth/refresh`
**Request body:** `application/json` → `RefreshTokenRequest` (required)

**Responses:** 200 → `ApiResponseRefreshTokenResponse`



## settings-controller

### GET `/api/settings/document-sequences`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `agencyId` (query, string(uuid), optional)

**Responses:** 200 → `ApiResponseListDocumentSequenceResponse`


### POST `/api/settings/document-sequences`
**Request body:** `application/json` → `UpsertDocumentSequenceRequest` (required)

**Responses:** 200 → `ApiResponseDocumentSequenceResponse`



## skill-controller

### GET `/api/v1/hrm/skills`
**Responses:** 200 → `ApiResponseListSkillResponse`


### POST `/api/v1/hrm/skills`
**Request body:** `application/json` → `CreateSkillRequest` (required)

**Responses:** 200 → `ApiResponseSkillResponse`


### GET `/api/v1/hrm/skills/employee-skills`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListEmployeeSkillResponse`


### POST `/api/v1/hrm/skills/employee-skills`
**Request body:** `application/json` → `CreateEmployeeSkillRequest` (required)

**Responses:** 200 → `ApiResponseEmployeeSkillResponse`


### GET `/api/v1/hrm/skills/employees/{employeeId}/skills`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListEmployeeSkillResponse`


### GET `/api/v1/hrm/skills/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseSkillResponse`


### GET `/api/v1/hrm/skills/{skillId}/employee-skills`
**Parameters:**
- `skillId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListEmployeeSkillResponse`



## social-declaration-controller

### GET `/api/v1/hrm/declarations`
**Parameters:**
- `orgId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListSocialDeclarationResponse`


### POST `/api/v1/hrm/declarations`
**Request body:** `application/json` → `CreateSocialDeclarationRequest` (required)

**Responses:** 200 → `ApiResponseSocialDeclarationResponse`


### GET `/api/v1/hrm/declarations/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseSocialDeclarationResponse`


### PUT `/api/v1/hrm/declarations/{id}/acknowledge`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseSocialDeclarationResponse`


### PUT `/api/v1/hrm/declarations/{id}/generate`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `GenerateRequest` (required)

**Responses:** 200 → `ApiResponseSocialDeclarationResponse`


### PUT `/api/v1/hrm/declarations/{id}/submit`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseSocialDeclarationResponse`



## statement-line-controller

### POST `/api/banking/statement-lines/{lineId}/ignore`
**Parameters:**
- `lineId` (path, string(uuid), required)

**Request body:** `application/json` → `ReasonRequest` (required)

**Responses:** 200 → `ApiResponseStatementLineResponse`


### POST `/api/treasury/statement-lines/{lineId}/ignore`
**Parameters:**
- `lineId` (path, string(uuid), required)

**Request body:** `application/json` → `ReasonRequest` (required)

**Responses:** 200 → `ApiResponseStatementLineResponse`



## supplier-controller

### GET `/api/suppliers`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `prospect` (query, boolean, optional)

**Responses:** 200 → `ApiResponseListThirdPartyResponse`


### POST `/api/suppliers`
**Request body:** `application/json` → `CreateCommercialThirdPartyRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### GET `/api/suppliers/by-accounting-account/{accountingAccount}`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `accountingAccount` (path, string, required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### GET `/api/suppliers/by-bank-account/{bankAccountNumber}`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `bankAccountNumber` (path, string, required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### GET `/api/suppliers/search`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `q` (query, string, required)
- `segment` (query, string, optional)
- `minimumQualificationScore` (query, integer(int32), optional)
- `active` (query, boolean, optional)
- `followUpStatus` (query, string, optional)
- `page` (query, integer(int32), optional)
- `size` (query, integer(int32), optional)

**Responses:** 200 → `ApiResponseListThirdPartySearchResponse`


### GET `/api/suppliers/statistics`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyStatisticsResponse`


### GET `/api/suppliers/without-accounting-account`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListThirdPartyResponse`


### GET `/api/suppliers/without-bank-account`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListThirdPartyResponse`


### DELETE `/api/suppliers/{thirdPartyId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### GET `/api/suppliers/{thirdPartyId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/suppliers/{thirdPartyId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateThirdPartyRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/suppliers/{thirdPartyId}/accounting-account`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `string` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/suppliers/{thirdPartyId}/activate`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/suppliers/{thirdPartyId}/bank-account`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `string` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### GET `/api/suppliers/{thirdPartyId}/bank-accounts`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListThirdPartyBankAccountResponse`


### POST `/api/suppliers/{thirdPartyId}/bank-accounts`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `ThirdPartyBankAccountRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyBankAccountResponse`


### DELETE `/api/suppliers/{thirdPartyId}/bank-accounts/{bankAccountId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)
- `bankAccountId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### PATCH `/api/suppliers/{thirdPartyId}/bank-accounts/{bankAccountId}/set-primary`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)
- `bankAccountId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### PATCH `/api/suppliers/{thirdPartyId}/deactivate`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/suppliers/{thirdPartyId}/follow-up/complete`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `ThirdPartyFollowUpRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/suppliers/{thirdPartyId}/follow-up/schedule`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `ThirdPartyFollowUpRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/suppliers/{thirdPartyId}/qualification`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `ThirdPartyQualificationRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### POST `/api/suppliers/{thirdPartyId}/resend-credentials`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### POST `/api/suppliers/{thirdPartyId}/score/recompute`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`



## system-audit-controller

### GET `/api/system-audits/integrity-check`
**Parameters:**
- `from` (query, string(date-time), optional)
- `to` (query, string(date-time), optional)
- `maxScan` (query, integer(int32), optional)
- `organizationId` (query, string(uuid), optional)

**Responses:** 200 → `ApiResponseIntegrityReport`


### GET `/api/system-audits/me`
**Parameters:**
- `limit` (query, integer(int32), optional)

**Responses:** 200 → `ApiResponseListSystemAuditResponse`


### GET `/api/system-audits/organization`
**Parameters:**
- `limit` (query, integer(int32), optional)

**Responses:** 200 → `ApiResponseListSystemAuditResponse`


### GET `/api/system-audits/organization/search`
**Parameters:**
- `action` (query, string, optional)
- `actorUserId` (query, string(uuid), optional)
- `from` (query, string(date-time), optional)
- `to` (query, string(date-time), optional)
- `page` (query, integer(int32), optional)
- `size` (query, integer(int32), optional)
- `organizationId` (query, string(uuid), optional)

**Responses:** 200 → `ApiResponseAuditPage`



## tax-bracket-admin-controller

### GET `/api/v1/payroll/tax-brackets`
**Parameters:**
- `countryCode` (query, string, required)

**Responses:** 200 → `ApiResponseListTaxBracketTableResponse`


### POST `/api/v1/payroll/tax-brackets`
**Request body:** `application/json` → `CreateTaxBracketTableRequest` (required)

**Responses:** 200 → `ApiResponseTaxBracketTableResponse`


### DELETE `/api/v1/payroll/tax-brackets/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseTaxBracketTableResponse`


### GET `/api/v1/payroll/tax-brackets/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseTaxBracketTableResponse`


### PUT `/api/v1/payroll/tax-brackets/{id}/activate`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseTaxBracketTableResponse`



## tax-controller

### DELETE `/api/accounting/taxes/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200


### GET `/api/accounting/taxes/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `Tax`


### PUT `/api/accounting/taxes/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `Tax` (required)

**Responses:** 200 → `Tax`



## third-party-address-book-controller

### GET `/api/third-parties/{thirdPartyId}/addresses`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListAddressResponse`


### POST `/api/third-parties/{thirdPartyId}/addresses`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `NestedAddressRequest` (required)

**Responses:** 200 → `ApiResponseAddressResponse`


### DELETE `/api/third-parties/{thirdPartyId}/addresses/{addressId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)
- `addressId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### GET `/api/third-parties/{thirdPartyId}/contacts`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListContactResponse`


### POST `/api/third-parties/{thirdPartyId}/contacts`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `NestedContactRequest` (required)

**Responses:** 200 → `ApiResponseContactResponse`


### DELETE `/api/third-parties/{thirdPartyId}/contacts/{contactId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)
- `contactId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`



## third-party-controller

### GET `/api/third-parties`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `role` (query, string, optional)
- `prospect` (query, boolean, optional)

**Responses:** 200 → `ApiResponseListThirdPartyResponse`


### POST `/api/third-parties`
**Request body:** `application/json` → `CreateThirdPartyRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### GET `/api/third-parties/search`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `q` (query, string, required)
- `role` (query, string, optional)
- `prospect` (query, boolean, optional)
- `segment` (query, string, optional)
- `minimumQualificationScore` (query, integer(int32), optional)
- `active` (query, boolean, optional)
- `followUpStatus` (query, string, optional)
- `page` (query, integer(int32), optional)
- `size` (query, integer(int32), optional)

**Responses:** 200 → `ApiResponseListThirdPartySearchResponse`


### DELETE `/api/third-parties/{thirdPartyId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### GET `/api/third-parties/{thirdPartyId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/third-parties/{thirdPartyId}`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateThirdPartyRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/third-parties/{thirdPartyId}/follow-up/complete`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `ThirdPartyFollowUpRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/third-parties/{thirdPartyId}/follow-up/schedule`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `ThirdPartyFollowUpRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### PATCH `/api/third-parties/{thirdPartyId}/qualification`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Request body:** `application/json` → `ThirdPartyQualificationRequest` (required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`


### POST `/api/third-parties/{thirdPartyId}/score/recompute`
**Parameters:**
- `thirdPartyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseThirdPartyResponse`



## timesheet-controller

### GET `/api/v1/hrm/timesheets`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `periode` (query, string, required)

**Responses:** 200 → `ApiResponseListTimesheetResponse`


### POST `/api/v1/hrm/timesheets`
**Request body:** `application/json` → `CreateTimesheetRequest` (required)

**Responses:** 200 → `ApiResponseTimesheetResponse`


### GET `/api/v1/hrm/timesheets/employee/{employeeId}`
**Parameters:**
- `employeeId` (path, string(uuid), required)
- `periode` (query, string, required)

**Responses:** 200 → `ApiResponseListTimesheetResponse`


### GET `/api/v1/hrm/timesheets/{timesheetId}`
**Parameters:**
- `timesheetId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseTimesheetResponse`


### PUT `/api/v1/hrm/timesheets/{timesheetId}/reject`
**Parameters:**
- `timesheetId` (path, string(uuid), required)

**Request body:** `application/json` → `RejectTimesheetRequest` (required)

**Responses:** 200 → `ApiResponseTimesheetResponse`


### PUT `/api/v1/hrm/timesheets/{timesheetId}/submit`
**Parameters:**
- `timesheetId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseTimesheetResponse`


### PUT `/api/v1/hrm/timesheets/{timesheetId}/validate`
**Parameters:**
- `timesheetId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseTimesheetResponse`



## training-budget-controller

### GET `/api/v1/hrm/training-budgets`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `annee` (query, integer(int32), required)

**Responses:** 200 → `ApiResponseListTrainingBudgetResponse`


### POST `/api/v1/hrm/training-budgets`
**Request body:** `application/json` → `CreateTrainingBudgetRequest` (required)

**Responses:** 200 → `ApiResponseTrainingBudgetResponse`


### GET `/api/v1/hrm/training-budgets/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseTrainingBudgetResponse`


### PUT `/api/v1/hrm/training-budgets/{id}/engage`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `MontantRequest` (required)

**Responses:** 200 → `ApiResponseTrainingBudgetResponse`


### PUT `/api/v1/hrm/training-budgets/{id}/realiser`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `MontantRequest` (required)

**Responses:** 200 → `ApiResponseTrainingBudgetResponse`



## training-controller

### GET `/api/v1/hrm/trainings`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListTrainingResponse`


### POST `/api/v1/hrm/trainings`
**Request body:** `application/json` → `PlanTrainingRequest` (required)

**Responses:** 200 → `ApiResponseTrainingResponse`


### GET `/api/v1/hrm/trainings/enrollments/employee/{employeeId}`
**Parameters:**
- `employeeId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListEnrollmentResponse`


### PUT `/api/v1/hrm/trainings/enrollments/{enrollmentId}/cancel`
**Parameters:**
- `enrollmentId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseEnrollmentResponse`


### PUT `/api/v1/hrm/trainings/enrollments/{enrollmentId}/complete`
**Parameters:**
- `enrollmentId` (path, string(uuid), required)

**Request body:** `application/json` → `CompleteEnrollmentRequest` (required)

**Responses:** 200 → `ApiResponseEnrollmentResponse`


### GET `/api/v1/hrm/trainings/requests`
**Parameters:**
- `employeeId` (query, string(uuid), optional)
- `organizationId` (query, string(uuid), optional)
- `status` (query, string, optional)

**Responses:** 200 → `ApiResponseListTrainingRequestResponse`


### POST `/api/v1/hrm/trainings/requests`
**Request body:** `application/json` → `TrainingRequestRequest` (required)

**Responses:** 200 → `ApiResponseTrainingRequestResponse`


### PUT `/api/v1/hrm/trainings/requests/{requestId}/approve`
**Parameters:**
- `requestId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseTrainingRequestResponse`


### PUT `/api/v1/hrm/trainings/requests/{requestId}/cancel`
**Parameters:**
- `requestId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseTrainingRequestResponse`


### PUT `/api/v1/hrm/trainings/requests/{requestId}/reject`
**Parameters:**
- `requestId` (path, string(uuid), required)

**Request body:** `application/json` → `RejectTrainingRequestRequest` (required)

**Responses:** 200 → `ApiResponseTrainingRequestResponse`


### GET `/api/v1/hrm/trainings/{trainingId}`
**Parameters:**
- `trainingId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseTrainingResponse`


### PUT `/api/v1/hrm/trainings/{trainingId}/cancel`
**Parameters:**
- `trainingId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseTrainingResponse`


### PUT `/api/v1/hrm/trainings/{trainingId}/complete`
**Parameters:**
- `trainingId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseTrainingResponse`


### GET `/api/v1/hrm/trainings/{trainingId}/enrollments`
**Parameters:**
- `trainingId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListEnrollmentResponse`


### POST `/api/v1/hrm/trainings/{trainingId}/enrollments`
**Parameters:**
- `trainingId` (path, string(uuid), required)

**Request body:** `application/json` → `EnrollRequest` (required)

**Responses:** 200 → `ApiResponseEnrollmentResponse`


### PUT `/api/v1/hrm/trainings/{trainingId}/start`
**Parameters:**
- `trainingId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseTrainingResponse`



## transaction-type-controller

### GET `/api/treasury/transaction-types`
**Responses:** 200 → `ApiResponseListTransactionTypeResponse`


### POST `/api/treasury/transaction-types`
**Request body:** `application/json` → `RegisterTransactionTypeRequest` (required)

**Responses:** 200 → `ApiResponseTransactionTypeResponse`


### GET `/api/treasury/transaction-types/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseTransactionTypeResponse`


### PUT `/api/treasury/transaction-types/{id}`
**Parameters:**
- `id` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateTransactionTypeRequest` (required)

**Responses:** 200 → `ApiResponseTransactionTypeResponse`


### POST `/api/treasury/transaction-types/{id}/activate`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseTransactionTypeResponse`


### POST `/api/treasury/transaction-types/{id}/deactivate`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseTransactionTypeResponse`



## treasury-controller

### GET `/api/treasury/bank-accounts`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListBankAccountResponse`


### POST `/api/treasury/bank-accounts`
**Request body:** `application/json` → `RegisterBankAccountRequest` (required)

**Responses:** 200 → `ApiResponseBankAccountResponse`


### GET `/api/treasury/bank-accounts/invoice-settlements`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `invoiceId` (query, string(uuid), optional)

**Responses:** 200 → `ApiResponseListInvoiceSettlementResponse`


### POST `/api/treasury/bank-accounts/invoice-settlements`
**Request body:** `application/json` → `RegisterInvoiceSettlementRequest` (required)

**Responses:** 200 → `ApiResponseInvoiceSettlementResponse`


### GET `/api/treasury/bank-accounts/invoice-settlements/{settlementId}`
**Parameters:**
- `settlementId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseInvoiceSettlementResponse`


### GET `/api/treasury/bank-accounts/reconciliations`
**Parameters:**
- `organizationId` (query, string(uuid), required)
- `bankAccountId` (query, string(uuid), optional)

**Responses:** 200 → `ApiResponseListReconciliationResponse`


### POST `/api/treasury/bank-accounts/reconciliations`
**Request body:** `application/json` → `OpenReconciliationRequest` (required)

**Responses:** 200 → `ApiResponseReconciliationResponse`


### GET `/api/treasury/bank-accounts/reconciliations/{reconciliationId}`
**Parameters:**
- `reconciliationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseReconciliationResponse`


### POST `/api/treasury/bank-accounts/reconciliations/{reconciliationId}/close`
**Parameters:**
- `reconciliationId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseReconciliationResponse`


### GET `/api/treasury/bank-accounts/{bankAccountId}`
**Parameters:**
- `bankAccountId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseBankAccountResponse`


### GET `/api/treasury/bank-accounts/{bankAccountId}/balance`
**Parameters:**
- `bankAccountId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseBalanceResponse`



## user-controller

### GET `/api/users/me`
**Responses:** 200 → `ApiResponseUserAccountResponse`


### PUT `/api/users/me/avatar`
**Request body:** `application/json` → `UpdateAvatarRequest` (required)

**Responses:** 200 → `ApiResponseUserAccountResponse`


### PUT `/api/users/me/identity-onboarding`
**Request body:** `application/json` → `UpdateIdentityOnboardingRequest` (required)

**Responses:** 200 → `ApiResponseUserAccountResponse`


### PUT `/api/users/me/onboarding`
**Request body:** `application/json` → `UpdateOnboardingRequest` (required)

**Responses:** 200 → `ApiResponseUserAccountResponse`


### PUT `/api/users/me/plan`
**Request body:** `application/json` → `UpdatePlanRequest` (required)

**Responses:** 200 → `ApiResponseUserAccountResponse`



## warehouse-controller

### GET `/api/warehouses`
**Responses:** 200 → `ApiResponseListAgencyResponse`


### POST `/api/warehouses`
**Request body:** `application/json` → `CreateAgencyRequest` (required)

**Responses:** 200 → `ApiResponseAgencyResponse`


### DELETE `/api/warehouses/{warehouseId}`
**Parameters:**
- `warehouseId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseVoid`


### PATCH `/api/warehouses/{warehouseId}`
**Parameters:**
- `warehouseId` (path, string(uuid), required)

**Request body:** `application/json` → `UpdateWarehouseRequest` (required)

**Responses:** 200 → `ApiResponseAgencyResponse`



## warehouse-layout-controller

### GET `/api/spare/warehouses/{agencyId}/layout`
**Parameters:**
- `agencyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseWarehouseLayoutResponse`


### PUT `/api/spare/warehouses/{agencyId}/layout`
**Parameters:**
- `agencyId` (path, string(uuid), required)

**Request body:** `application/json` → `WarehouseLayoutRequest` (required)

**Responses:** 200 → `ApiResponseWarehouseLayoutResponse`



## warehouse-location-controller

### GET `/api/spare/warehouses/products/{productId}/locations`
**Parameters:**
- `productId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListProductLocationResponse`


### GET `/api/spare/warehouses/{agencyId}/product-locations`
**Parameters:**
- `agencyId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseListProductLocationResponse`


### PUT `/api/spare/warehouses/{agencyId}/product-locations/{productId}`
**Parameters:**
- `agencyId` (path, string(uuid), required)
- `productId` (path, string(uuid), required)

**Request body:** `application/json` → `UpsertProductLocationRequest` (required)

**Responses:** 200 → `ApiResponseProductLocationResponse`



## warehouse-transfer-controller

### GET `/api/inventory/transfers`
**Parameters:**
- `organizationId` (query, string(uuid), required)

**Responses:** 200 → `ApiResponseListWarehouseTransferResponse`


### POST `/api/inventory/transfers`
**Request body:** `application/json` → `CreateWarehouseTransferRequest` (required)

**Responses:** 200 → `ApiResponseWarehouseTransferResponse`


### POST `/api/inventory/transfers/{transferId}/complete`
**Parameters:**
- `transferId` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseWarehouseTransferResponse`



## workflow-request-controller

### GET `/api/spare/workflow/requests`
**Parameters:**
- `status` (query, string, optional)
- `type` (query, string, optional)

**Responses:** 200 → `ApiResponseListWorkflowRequestResponse`


### POST `/api/spare/workflow/requests`
**Request body:** `application/json` → `CreateWorkflowRequest` (required)

**Responses:** 200 → `ApiResponseWorkflowRequestResponse`


### POST `/api/spare/workflow/requests/{id}/approve`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseWorkflowRequestResponse`


### POST `/api/spare/workflow/requests/{id}/reject`
**Parameters:**
- `id` (path, string(uuid), required)

**Responses:** 200 → `ApiResponseWorkflowRequestResponse`


