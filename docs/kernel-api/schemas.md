# Kernel API — Schemas Reference

Auto-generated from `openapi.json`. 1078 component schemas. Referenced by name from `endpoints.md` (request/response bodies).

**Do not edit by hand** — see regeneration instructions in `endpoints.md`.

## AccountConnectorTypeResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `code` | `string` | no |
| `name` | `string` | no |
| `active` | `boolean` | no |

## AccountResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `accountNumber` | `string` | no |
| `label` | `string` | no |
| `sense` | `string` | no |
| `usable` | `boolean` | no |
| `organizationId` | `string(uuid)` | no |

## AccountSubTypeResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `accountTypeId` | `string(uuid)` | no |
| `code` | `string` | no |
| `libelle` | `string` | no |
| `description` | `string` | no |
| `peutEmettreCheques` | `boolean` | no |
| `peutRecevoirCheques` | `boolean` | no |
| `peutTransactionsEspeces` | `boolean` | no |
| `decouvertAutorise` | `boolean` | no |
| `ordreAffichage` | `integer(int32)` | no |
| `active` | `boolean` | no |

## AccountTypeResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `code` | `string` | no |
| `libelle` | `string` | no |
| `description` | `string` | no |
| `peutEmettreCheques` | `boolean` | no |
| `peutRecevoirCheques` | `boolean` | no |
| `peutTransactionsEspeces` | `boolean` | no |
| `decouvertAutorise` | `boolean` | no |
| `decouvertParDefaut` | `number` | no |
| `ordreAffichage` | `integer(int32)` | no |
| `active` | `boolean` | no |

## AccountView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `accountNumber` | `string` | no |
| `label` | `string` | no |
| `accountType` | `string` | no |
| `externalId` | `string(uuid)` | no |
| `active` | `boolean` | no |
| `notes` | `string` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |

## AccountingClosingPreviewView

| Field | Type | Required |
|---|---|---|
| `organization` | `OrganizationSummaryView` | no |
| `journalCount` | `integer(int32)` | no |
| `invoiceCount` | `integer(int32)` | no |
| `draftInvoiceCount` | `integer(int32)` | no |
| `postedInvoiceCount` | `integer(int32)` | no |
| `totalReceivables` | `number` | no |
| `totalPayables` | `number` | no |
| `ready` | `boolean` | no |
| `blockingIssues` | `string[]` | no |

## AccountingDashboardView

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `closingReady` | `boolean` | no |
| `fiscalYearsCount` | `integer(int32)` | no |
| `openPeriodsCount` | `integer(int64)` | no |
| `draftDeclarationsCount` | `integer(int64)` | no |
| `fixedAssetsCount` | `integer(int32)` | no |
| `attachmentsCount` | `integer(int32)` | no |
| `pendingNotificationsCount` | `integer(int32)` | no |
| `activeSynchronizationJobsCount` | `integer(int32)` | no |

## AccountingEntryView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `journalId` | `string(uuid)` | no |
| `reference` | `string` | no |
| `entryDate` | `string(date-time)` | no |
| `status` | `string` | no |
| `lines` | `EntryLineView[]` | no |
| `totalDebit` | `number` | no |
| `totalCredit` | `number` | no |
| `createdAt` | `string(date-time)` | no |
| `validatedAt` | `string(date-time)` | no |
| `cancelledAt` | `string(date-time)` | no |

## AccountingJournalResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `code` | `string` | no |
| `label` | `string` | no |
| `type` | `string` | no |
| `notes` | `string` | no |
| `active` | `boolean` | no |

## AccountingOperationView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `operationType` | `string` | no |
| `reference` | `string` | no |
| `amount` | `number` | no |
| `currency` | `string` | no |
| `createdAt` | `string(date-time)` | no |

## AccountingPeriodResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `fiscalYearId` | `string(uuid)` | no |
| `startDate` | `string(date)` | no |
| `endDate` | `string(date)` | no |
| `status` | `string` | no |
| `organizationId` | `string(uuid)` | no |

## AccountingPeriodView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `fiscalYearId` | `string(uuid)` | no |
| `code` | `string` | no |
| `startDate` | `string(date)` | no |
| `endDate` | `string(date)` | no |
| `status` | `string` | no |
| `createdAt` | `string(date-time)` | no |
| `closedAt` | `string(date-time)` | no |

## AccountingReferenceDataView

| Field | Type | Required |
|---|---|---|
| `currentOrganization` | `OrganizationSummaryView` | no |
| `accessibleOrganizations` | `OrganizationSummaryView[]` | no |
| `journals` | `JournalSummaryView[]` | no |
| `documentSequences` | `DocumentSequenceSummaryView[]` | no |
| `invoices` | `InvoiceSummaryView[]` | no |
| `openReceivables` | `OpenItemSummaryView[]` | no |
| `openPayables` | `OpenItemSummaryView[]` | no |
| `customers` | `ThirdPartySummaryView[]` | no |
| `suppliers` | `ThirdPartySummaryView[]` | no |

## AccountingSettingView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `code` | `string` | no |
| `value` | `string` | no |
| `updatedAt` | `string(date-time)` | no |

## ActorResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `firstName` | `string` | no |
| `lastName` | `string` | no |
| `name` | `string` | no |
| `displayName` | `string` | no |
| `phoneNumber` | `string` | no |
| `email` | `string` | no |
| `description` | `string` | no |
| `type` | `string` | no |
| `gender` | `string` | no |
| `photoUri` | `string` | no |
| `photoId` | `string(uuid)` | no |
| `nationality` | `string` | no |
| `birthDate` | `string(date)` | no |
| `profession` | `string` | no |
| `biography` | `string` | no |
| `addresses` | `string(uuid)[]` | no |
| `contacts` | `string(uuid)[]` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |
| `deletedAt` | `string(date-time)` | no |

## AddCheckToDepositRequest

| Field | Type | Required |
|---|---|---|
| `checkId` | `string(uuid)` | yes |
| `amount` | `number` | yes |

## AddConnectorFieldRequest

| Field | Type | Required |
|---|---|---|
| `fieldName` | `string` | yes |
| `fieldLabel` | `string` | yes |
| `fieldType` | `string` | yes |
| `required` | `boolean` | no |
| `displayOrder` | `integer(int32)` | no |

## AddContractRequest

| Field | Type | Required |
|---|---|---|
| `type` | `string` | no |
| `position` | `string` | no |
| `dateDebut` | `string(date)` | no |
| `dateFin` | `string(date)` | no |
| `salaireBase` | `number` | no |
| `avantagesNature` | `number` | no |
| `periodeEssai` | `integer(int32)` | no |
| `documentFileId` | `string(uuid)` | no |

## AddDependentRequest

| Field | Type | Required |
|---|---|---|
| `nom` | `string` | no |
| `prenom` | `string` | no |
| `dateNaissance` | `string(date)` | no |
| `lienParente` | `string` | no |

## AddEmergencyContactRequest

| Field | Type | Required |
|---|---|---|
| `nom` | `string` | no |
| `prenom` | `string` | no |
| `relation` | `string` | no |
| `telephone` | `string` | no |
| `email` | `string` | no |
| `priorite` | `integer(int32)` | no |

## AddExpenseLineRequest

| Field | Type | Required |
|---|---|---|
| `description` | `string` | no |
| `montant` | `number` | no |
| `categorie` | `string` | no |
| `justificatifFileId` | `string(uuid)` | no |

## AddObjectiveRequest

| Field | Type | Required |
|---|---|---|
| `description` | `string` | no |
| `poids` | `number` | no |

## AddVariantAttributeRequest

| Field | Type | Required |
|---|---|---|
| `attributeName` | `string` | yes |
| `attributeValue` | `string` | yes |

## AddressResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `addressableType` | `string` | no |
| `addressableId` | `string(uuid)` | no |
| `type` | `string` | no |
| `addressLine1` | `string` | no |
| `addressLine2` | `string` | no |
| `city` | `string` | no |
| `state` | `string` | no |
| `locality` | `string` | no |
| `countryId` | `string(uuid)` | no |
| `zipCode` | `string` | no |
| `postalCode` | `string` | no |
| `poBox` | `string` | no |
| `isDefault` | `boolean` | no |
| `neighborhood` | `string` | no |
| `informalDescription` | `string` | no |
| `latitude` | `number(double)` | no |
| `longitude` | `number(double)` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |
| `deletedAt` | `string(date-time)` | no |

## AdminResetPasswordResponse

| Field | Type | Required |
|---|---|---|
| `userId` | `string(uuid)` | no |
| `username` | `string` | no |
| `email` | `string` | no |
| `temporaryPassword` | `string` | no |

## AdministrationAuditResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `actorUserId` | `string(uuid)` | no |
| `action` | `string` | no |
| `targetType` | `string` | no |
| `targetId` | `string` | no |
| `payloadSummary` | `string` | no |
| `occurredAt` | `string(date-time)` | no |

## AdministrationGeneralOptionsResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `negotiateSellingPrice` | `boolean` | no |
| `sellingPriceIncludeVat` | `boolean` | no |
| `authorizeExceptionalDiscount` | `boolean` | no |
| `grantableDiscountRate` | `number(double)` | no |
| `printLogo` | `boolean` | no |
| `paperFormat` | `string` | no |
| `lengthOfVatInvoiceNumber` | `integer(int32)` | no |
| `prefixOfVatInvoiceNumber` | `string` | no |
| `lowStockAlert` | `boolean` | no |
| `preventiveMaintenanceAlert` | `boolean` | no |
| `defaultCurrency` | `string` | no |
| `legalIdentity` | `string` | no |
| `taxIdentifier` | `string` | no |
| `requireSalesOrderApproval` | `boolean` | no |
| `requireReturnApproval` | `boolean` | no |

## AdministrationPermissionResponse

| Field | Type | Required |
|---|---|---|
| `code` | `string` | no |
| `name` | `string` | no |
| `description` | `string` | no |
| `module` | `string` | no |
| `scope` | `string` | no |
| `system` | `boolean` | no |
| `assignable` | `boolean` | no |
| `deprecated` | `boolean` | no |

## AdministrationRoleResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `code` | `string` | no |
| `name` | `string` | no |
| `scopeType` | `string` | no |
| `permissions` | `string[]` | no |

## AdministrationUserResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `actorId` | `string(uuid)` | no |
| `username` | `string` | no |
| `email` | `string` | no |
| `phoneNumber` | `string` | no |
| `status` | `string` | no |
| `plan` | `string` | no |
| `accountType` | `string` | no |
| `onboardingStatus` | `string` | no |
| `onboardingStep` | `integer(int32)` | no |
| `emailVerified` | `boolean` | no |
| `emailVerifiedAt` | `string(date-time)` | no |
| `phoneVerified` | `boolean` | no |
| `mfaEnabled` | `boolean` | no |
| `mfaChannel` | `string` | no |
| `forcePasswordChange` | `boolean` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |

## AdministrationUserRoleAssignmentResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `userId` | `string(uuid)` | no |
| `roleId` | `string(uuid)` | no |
| `scopeType` | `string` | no |
| `scopeId` | `string(uuid)` | no |
| `scope` | `string` | no |

## AdministrativePlatformOptionsResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `requireBusinessActorApproval` | `boolean` | no |
| `requireOrganizationApproval` | `boolean` | no |
| `allowOrganizationSelfServiceCreation` | `boolean` | no |
| `allowAgencySelfServiceCreation` | `boolean` | no |
| `allowRoleCloning` | `boolean` | no |
| `allowAgencyScopedCustomRoles` | `boolean` | no |
| `allowOrganizationAdminsToGovernAgencies` | `boolean` | no |
| `allowBusinessActorSelfReactivation` | `boolean` | no |

## AdministrativeRoleTemplateResponse

| Field | Type | Required |
|---|---|---|
| `code` | `string` | no |
| `name` | `string` | no |
| `scopeType` | `string` | no |
| `permissions` | `string[]` | no |
| `protectedTemplate` | `boolean` | no |

## AdvancedAssetOverview

| Field | Type | Required |
|---|---|---|
| `totalAssets` | `integer(int32)` | no |
| `compliantAssets` | `integer(int64)` | no |
| `nonCompliantAssets` | `integer(int64)` | no |
| `assetsWithExpiringWarranty` | `integer(int64)` | no |
| `totalAcquisitionCost` | `number` | no |
| `totalCurrentValue` | `number` | no |

## AdvancedAssetOverviewSnapshot

| Field | Type | Required |
|---|---|---|
| `totalAssets` | `integer(int32)` | no |
| `compliantAssets` | `integer(int64)` | no |
| `nonCompliantAssets` | `integer(int64)` | no |
| `assetsWithExpiringWarranty` | `integer(int64)` | no |
| `totalAcquisitionCost` | `number` | no |
| `totalCurrentValue` | `number` | no |

## AgedBalance

| Field | Type | Required |
|---|---|---|
| `referenceDate` | `string(date)` | no |
| `customers` | `AgedBalanceLine[]` | no |
| `suppliers` | `AgedBalanceLine[]` | no |

## AgedBalanceLine

| Field | Type | Required |
|---|---|---|
| `accountNumber` | `string` | no |
| `label` | `string` | no |
| `totalBalance` | `number` | no |
| `tranche0to30` | `number` | no |
| `tranche31to60` | `number` | no |
| `tranche61to90` | `number` | no |
| `trancheOver90` | `number` | no |

## AgencyAffiliationResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `actorId` | `string(uuid)` | no |
| `type` | `string` | no |
| `active` | `boolean` | no |

## AgencyDomainResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `domainId` | `string(uuid)` | no |

## AgencyOpenStatusResponse

| Field | Type | Required |
|---|---|---|
| `open` | `boolean` | no |
| `status` | `string` | no |

## AgencyOperationalPilotageView

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `siteProfile` | `OperationalSiteProfileView` | no |
| `siteReadiness` | `OperationalSiteReadinessSnapshot` | no |
| `operationalPolicyId` | `string(uuid)` | no |
| `assetPortfolio` | `AssetPortfolioSnapshot` | no |
| `operationalSite` | `OperationalSiteViewSnapshot` | no |
| `campaignSummary` | `CampaignSummaryView` | no |
| `agencyDocumentCount` | `integer(int32)` | no |

## AgencyResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `governanceStatus` | `string` | no |
| `governedByUserId` | `string(uuid)` | no |
| `governedAt` | `string(date-time)` | no |
| `governanceReason` | `string` | no |
| `code` | `string` | no |
| `ownerId` | `string(uuid)` | no |
| `managerId` | `string(uuid)` | no |
| `name` | `string` | no |
| `location` | `string` | no |
| `description` | `string` | no |
| `transferable` | `boolean` | no |
| `active` | `boolean` | no |
| `logoUri` | `string` | no |
| `logoId` | `string(uuid)` | no |
| `shortName` | `string` | no |
| `longName` | `string` | no |
| `isIndividualBusiness` | `boolean` | no |
| `isHeadquarter` | `boolean` | no |
| `country` | `string` | no |
| `city` | `string` | no |
| `latitude` | `number(double)` | no |
| `longitude` | `number(double)` | no |
| `openTime` | `string` | no |
| `closeTime` | `string` | no |
| `phone` | `string` | no |
| `email` | `string` | no |
| `whatsapp` | `string` | no |
| `greetingMessage` | `string` | no |
| `averageRevenue` | `number` | no |
| `capitalShare` | `number` | no |
| `registrationNumber` | `string` | no |
| `socialNetwork` | `string` | no |
| `taxNumber` | `string` | no |
| `keywords` | `string[]` | no |
| `isPublic` | `boolean` | no |
| `isBusiness` | `boolean` | no |
| `totalAffiliatedCustomers` | `integer(int32)` | no |
| `deletedAt` | `string(date-time)` | no |
| `agencyType` | `string` | no |

## AgencyScheduleResponse

| Field | Type | Required |
|---|---|---|
| `regularRules` | `OpeningHoursResponse[]` | no |
| `upcomingExceptions` | `OpeningHoursExceptionResponse[]` | no |

## AgencyScopedResourceRequest

| Field | Type | Required |
|---|---|---|
| `resourceCode` | `string` | yes |
| `name` | `string` | yes |
| `category` | `string` | yes |
| `serialNumber` | `string` | yes |
| `latitude` | `number(double)` | no |
| `longitude` | `number(double)` | no |
| `ipAddress` | `string` | no |
| `macAddress` | `string` | no |

## AgentStats

| Field | Type | Required |
|---|---|---|
| `agentType` | `string` | no |
| `coveredZones` | `string` | no |
| `specializations` | `string` | no |
| `commissionRate` | `number` | no |
| `contractStartDate` | `string(date)` | no |
| `contractEndDate` | `string(date)` | no |

## AmendMissionOrderRequest

| Field | Type | Required |
|---|---|---|
| `destination` | `string` | no |
| `objet` | `string` | no |
| `dateDebut` | `string(date)` | no |
| `dateFin` | `string(date)` | no |
| `montantAvance` | `number` | no |
| `centreCout` | `string` | no |

## AnchorDocumentRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `chainCode` | `string` | no |
| `sourceService` | `string` | no |
| `sourceReference` | `string` | no |
| `documentHash` | `string` | no |
| `metadata` | `string` | no |

## ApiResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `?` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseAccountConnectorTypeResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AccountConnectorTypeResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseAccountResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AccountResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseAccountSubTypeResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AccountSubTypeResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseAccountTypeResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AccountTypeResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseAccountingPeriodResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AccountingPeriodResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseActorResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ActorResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseAddressResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AddressResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseAdminResetPasswordResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AdminResetPasswordResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseAdministrationGeneralOptionsResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AdministrationGeneralOptionsResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseAdministrationRoleResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AdministrationRoleResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseAdministrationUserRoleAssignmentResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AdministrationUserRoleAssignmentResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseAdministrativePlatformOptionsResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AdministrativePlatformOptionsResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseAdvancedAssetOverview

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AdvancedAssetOverview` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseAgencyAffiliationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AgencyAffiliationResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseAgencyDomainResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AgencyDomainResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseAgencyOpenStatusResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AgencyOpenStatusResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseAgencyOperationalPilotageView

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AgencyOperationalPilotageView` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseAgencyResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AgencyResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseAgencyScheduleResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AgencyScheduleResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseAppBusinessSettingsResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AppBusinessSettingsResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseApplicationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ApplicationResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseArtistProfileResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ArtistProfileResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseArtworkCommentResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ArtworkCommentResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseArtworkInvoiceLinkResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ArtworkInvoiceLinkResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseArtworkLikeResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ArtworkLikeResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseArtworkMediaResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ArtworkMediaResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseArtworkProductLinkResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ArtworkProductLinkResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseArtworkResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ArtworkResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseArtworkSaleLinkResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ArtworkSaleLinkResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseAssetPortfolioView

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AssetPortfolioView` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseAssetProfileResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AssetProfileResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseAuditPage

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AuditPage` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseAutoMatchResult

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AutoMatchResult` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseBalanceDesComptesDto

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `BalanceDesComptesDto` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseBalanceResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `BalanceResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseBankAccountResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `BankAccountResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseBankCategoryResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `BankCategoryResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseBankResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `BankResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseBankStatementResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `BankStatementResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseBankTransactionResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `BankTransactionResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseBatchResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `BatchResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseBilanDto

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `BilanDto` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseBlockResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `BlockResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseBoolean

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `boolean` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseBrouillardComptableDto

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `BrouillardComptableDto` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseBusinessActorResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `BusinessActorResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseBusinessDomainResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `BusinessDomainResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseCampaignResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `CampaignResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseCaptchaChallengeResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `CaptchaChallengeResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseCaptchaVerificationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `CaptchaVerificationResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseCashFlowDto

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `CashFlowDto` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseCatalogServiceView

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `CatalogServiceView` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseCategoryI18nResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `CategoryI18nResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseCertificationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `CertificationResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseChainValidationReport

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ChainValidationReport` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseCheckDepositResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `CheckDepositResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseCheckPaymentResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `CheckPaymentResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseCheckbookResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `CheckbookResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseCheckbookStats

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `CheckbookStats` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseClientApplicationPlanResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ClientApplicationPlanResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseClientApplicationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ClientApplicationResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseCommercialPlanCatalogResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `CommercialPlanCatalogResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseCommercialSubscriptionCatalogResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `CommercialSubscriptionCatalogResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseCompteResultatDto

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `CompteResultatDto` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseConnectorFieldResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ConnectorFieldResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseContactResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ContactResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseContextualLoginResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ContextualLoginResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseContractResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ContractResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseCsvImportReport

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `CsvImportReport` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseCsvTemplateResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `CsvTemplateResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseDataSourceResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `DataSourceResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseDeclarationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `DeclarationResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseDepartmentMemberResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `DepartmentMemberResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseDepartmentResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `DepartmentResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseDependentResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `DependentResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseDiscoverLoginContextsResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `DiscoverLoginContextsResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseDiscoverSignUpContextsResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `DiscoverSignUpContextsResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseDocumentGovernanceOverview

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `DocumentGovernanceOverview` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseDocumentHubOverview

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `DocumentHubOverview` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseDocumentLinkView

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `DocumentLinkView` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseDocumentResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `DocumentResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseDocumentReviewResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `DocumentReviewResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseDocumentSequenceResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `DocumentSequenceResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseDocumentVerification

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `DocumentVerification` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseEmergencyContactResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `EmergencyContactResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseEmployeeMembershipResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `EmployeeMembershipResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseEmployeeProfileResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `EmployeeProfileResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseEmployeeResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `EmployeeResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseEmployeeSkillResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `EmployeeSkillResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseEnrollmentResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `EnrollmentResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseExecutiveSummaryDto

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ExecutiveSummaryDto` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseExpenseLineResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ExpenseLineResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseExpenseReportResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ExpenseReportResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseFinalSettlementResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `FinalSettlementResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseFiscalYearResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `FiscalYearResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseForgotPasswordResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ForgotPasswordResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseGalleryEventResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `GalleryEventResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseGalleryReservationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `GalleryReservationResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseGalleryTicketResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `GalleryTicketResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseGarnishmentResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `GarnishmentResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseGeneralizedInventoryCampaignResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `GeneralizedInventoryCampaignResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseGeneralizedInventoryView

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `GeneralizedInventoryView` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseGeneratedWalletResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `GeneratedWalletResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseIdentifyAccountResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `IdentifyAccountResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseInteger

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `integer(int32)` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseIntegrityReport

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `IntegrityReport` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseInterviewResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `InterviewResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseInventorySessionResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `InventorySessionResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseInvoiceResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `InvoiceResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseInvoiceSettlementResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `InvoiceSettlementResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseIssuedAuthChallengeResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `IssuedAuthChallengeResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseJobOfferResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `JobOfferResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseJournalEntryResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `JournalEntryResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseLeaveResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `LeaveResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseLedgerResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `LedgerResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListAccountConnectorTypeResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AccountConnectorTypeResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListAccountResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AccountResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListAccountSubTypeResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AccountSubTypeResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListAccountTypeResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AccountTypeResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListAccountingJournalResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AccountingJournalResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListAccountingPeriodResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AccountingPeriodResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListAddressResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AddressResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListAdministrationAuditResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AdministrationAuditResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListAdministrationPermissionResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AdministrationPermissionResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListAdministrationRoleResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AdministrationRoleResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListAdministrationUserResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AdministrationUserResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListAdministrationUserRoleAssignmentResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AdministrationUserRoleAssignmentResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListAdministrativeRoleTemplateResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AdministrativeRoleTemplateResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListAgencyAffiliationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AgencyAffiliationResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListAgencyDomainResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AgencyDomainResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListAgencyResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AgencyResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListApplicationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ApplicationResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListArtworkCommentResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ArtworkCommentResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListArtworkLikeResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ArtworkLikeResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListArtworkMediaResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ArtworkMediaResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListArtworkResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ArtworkResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListAssetProfileResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AssetProfileResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListAuditLogResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `AuditLogResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListBankAccountResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `BankAccountResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListBankCategoryResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `BankCategoryResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListBankResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `BankResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListBankStatementResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `BankStatementResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListBankTransactionResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `BankTransactionResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListBatchResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `BatchResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListBlockResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `BlockResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListBrouillardComptableDto

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `BrouillardComptableDto[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListBusinessActorResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `BusinessActorResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListBusinessDomainResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `BusinessDomainResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListCampaignResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `CampaignResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListCatalogServiceView

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `CatalogServiceView[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListCategoryI18nResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `CategoryI18nResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListCertificationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `CertificationResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListCheckDepositResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `CheckDepositResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListCheckPaymentResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `CheckPaymentResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListCheckbookResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `CheckbookResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListClientApplicationPlanResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ClientApplicationPlanResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListClientApplicationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ClientApplicationResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListConnectorFieldResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ConnectorFieldResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListContactResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ContactResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListContractResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ContractResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListDepartmentMemberResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `DepartmentMemberResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListDepartmentResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `DepartmentResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListDependentResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `DependentResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListDocumentLinkView

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `DocumentLinkView[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListDocumentResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `DocumentResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListDocumentSequenceResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `DocumentSequenceResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListDocumentStatusView

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `DocumentStatusView[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListEmergencyContactResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `EmergencyContactResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListEmployeeMembershipResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `EmployeeMembershipResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListEmployeeResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `EmployeeResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListEmployeeSkillResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `EmployeeSkillResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListEnrollmentResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `EnrollmentResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListExpenseLineResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ExpenseLineResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListExpenseReportResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ExpenseReportResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListFinalSettlementResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `FinalSettlementResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListFiscalYearResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `FiscalYearResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListGalleryEventResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `GalleryEventResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListGalleryReservationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `GalleryReservationResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListGarnishmentResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `GarnishmentResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListGrandLivreDto

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `GrandLivreDto[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListInterviewResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `InterviewResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListInventorySessionResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `InventorySessionResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListInvoiceResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `InvoiceResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListInvoiceSettlementResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `InvoiceSettlementResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListJobOfferResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `JobOfferResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListLeaveBalanceResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `LeaveBalanceResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListLeaveResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `LeaveResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListLedgerResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `LedgerResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListLoanAdvanceResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `LoanAdvanceResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListLoanRepaymentResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `LoanRepaymentResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListLookupTableResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `LookupTableResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListMaintenanceRecordResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `MaintenanceRecordResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListMaterialRequestResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `MaterialRequestResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListMaterialResourceResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `MaterialResourceResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListMaterialResourceSearchResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `MaterialResourceSearchResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListMediaAssetResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `MediaAssetResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListMedicalCertificateResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `MedicalCertificateResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListMedicalVisitResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `MedicalVisitResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListMissionOrderResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `MissionOrderResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListMyPayslipResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `MyPayslipResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListObjectiveResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ObjectiveResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListOnboardingTaskResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OnboardingTaskResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListOpenAccountingItemResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OpenAccountingItemResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListOpeningHoursResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OpeningHoursResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListOperationTemplateResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OperationTemplateResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListOperationalResponsibilityResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OperationalResponsibilityResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListOrganizationActorResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OrganizationActorResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListOrganizationDomainResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OrganizationDomainResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListOrganizationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OrganizationResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListOrganizationRoleResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OrganizationRoleResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListOrganizationSearchResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OrganizationSearchResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListOrganizationServiceCatalogResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OrganizationServiceCatalogResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListOrganizationServicePackResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OrganizationServicePackResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListPayElementResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `PayElementResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListPayVariableResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `PayVariableResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListPayrollEmployeeResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `PayrollEmployeeResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListPayrollEntryResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `PayrollEntryResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListPayrollRunResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `PayrollRunResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListPayslipLineResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `PayslipLineResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListPhysicalSpaceResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `PhysicalSpaceResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListPlanResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `PlanResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListPointOfInterestResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `PointOfInterestResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListProductCategoryResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ProductCategoryResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListProductLocationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ProductLocationResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListProductPriceResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ProductPriceResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListProductResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ProductResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListProductSearchResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ProductSearchResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListProductTransformationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ProductTransformationResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListProposedActivityResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ProposedActivityResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListPurchaseOrderResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `PurchaseOrderResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListReceiptResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ReceiptResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListReconciliationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ReconciliationResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListReconciliationSuggestionResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ReconciliationSuggestionResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListResourceAssignmentResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ResourceAssignmentResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListResourceLocationObservationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ResourceLocationObservationResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListResourceNetworkObservationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ResourceNetworkObservationResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListResourceReservationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ResourceReservationResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListRetroactiveResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `RetroactiveResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListReviewResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ReviewResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListRhKpiSnapshotResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `RhKpiSnapshotResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListSalesOrderResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `SalesOrderResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListSkillResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `SkillResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListSocialDeclarationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `SocialDeclarationResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListStatementLineResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `StatementLineResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListStockMovementResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `StockMovementResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListSubscriptionResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `SubscriptionResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListSystemAuditResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `SystemAuditResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListTaxBracketTableResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `TaxBracketTableResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListThirdPartyBankAccountResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ThirdPartyBankAccountResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListThirdPartyResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ThirdPartyResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListThirdPartySearchResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ThirdPartySearchResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListTimelineEntryView

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `TimelineEntryView[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListTimelineEventResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `TimelineEventResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListTimesheetResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `TimesheetResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListTrainingBudgetResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `TrainingBudgetResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListTrainingRequestResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `TrainingRequestResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListTrainingResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `TrainingResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListTransactionResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `TransactionResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListTransactionTypeResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `TransactionTypeResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListVariantAttributeResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `VariantAttributeResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListVariantPriceResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `VariantPriceResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListVariantResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `VariantResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListWalletResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `WalletResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListWarehouseTransferResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `WarehouseTransferResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseListWorkflowRequestResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `WorkflowRequestResponse[]` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseLoanAdvanceResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `LoanAdvanceResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseLoginResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `LoginResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseLong

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `integer(int64)` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseLookupTableResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `LookupTableResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseMapStringObject

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `object` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseMaterialRequestResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `MaterialRequestResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseMaterialResourceResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `MaterialResourceResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseMediaAssetResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `MediaAssetResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseMedicalCertificateResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `MedicalCertificateResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseMedicalVisitResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `MedicalVisitResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseMissionOrderResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `MissionOrderResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseMyClientApplicationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `MyClientApplicationResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseObject

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `?` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseObjectiveResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ObjectiveResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseOnboardingTaskResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OnboardingTaskResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseOpeningHoursExceptionResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OpeningHoursExceptionResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseOpeningHoursResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OpeningHoursResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseOperationTemplateResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OperationTemplateResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseOperationalComplianceOverview

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OperationalComplianceOverview` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseOperationalPolicyResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OperationalPolicyResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseOperationalResponsibilityResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OperationalResponsibilityResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseOperationalSiteProfileResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OperationalSiteProfileResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseOperationalSiteReadinessView

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OperationalSiteReadinessView` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseOperationalSiteView

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OperationalSiteView` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseOrganizationActorResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OrganizationActorResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseOrganizationCommercialSubscriptionResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OrganizationCommercialSubscriptionResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseOrganizationDomainResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OrganizationDomainResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseOrganizationOperationalPilotageView

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OrganizationOperationalPilotageView` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseOrganizationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OrganizationResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseOrganizationServicesResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OrganizationServicesResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseOtpChallengeResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OtpChallengeResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseOtpVerificationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `OtpVerificationResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponsePayElementResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `PayElementResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponsePayVariableResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `PayVariableResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponsePayrollEmployeeResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `PayrollEmployeeResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponsePayrollOnboardingManifest

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `PayrollOnboardingManifest` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponsePayrollRunResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `PayrollRunResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponsePersonalInfoResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `PersonalInfoResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponsePhysicalSpaceResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `PhysicalSpaceResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponsePlanResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `PlanResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponsePointOfInterestResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `PointOfInterestResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponsePolicyResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `PolicyResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseProductCategoryResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ProductCategoryResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseProductLocationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ProductLocationResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseProductPriceResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ProductPriceResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseProductResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ProductResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseProductSpecResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ProductSpecResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseProductTransformationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ProductTransformationResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseProposedActivityResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ProposedActivityResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseProvisionedClientApplicationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ProvisionedClientApplicationResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponsePurchaseOrderResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `PurchaseOrderResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseReceiptResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ReceiptResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseReconciliationMatchResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ReconciliationMatchResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseReconciliationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ReconciliationResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseReconciliationRunResult

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ReconciliationRunResult` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseReconciliationSummary

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ReconciliationSummary` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseRefreshTokenResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `RefreshTokenResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseResourceLocationObservationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ResourceLocationObservationResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseResourceNetworkObservationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ResourceNetworkObservationResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseRetroactiveResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `RetroactiveResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseReviewResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ReviewResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseRhKpiSnapshotResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `RhKpiSnapshotResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseRoleResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `RoleResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseSalesOrderResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `SalesOrderResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseServiceWorkspaceView

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ServiceWorkspaceView` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseSignatureResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `SignatureResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseSigningPayloadResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `SigningPayloadResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseSkillResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `SkillResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseSocialDeclarationResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `SocialDeclarationResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseStatementLineResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `StatementLineResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseStatementUploadResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `StatementUploadResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseStockBalanceResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `StockBalanceResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseStockMovementResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `StockMovementResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseStoredFileResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `StoredFileResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseSubscriptionResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `SubscriptionResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseTaxBracketTableResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `TaxBracketTableResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseThirdPartyBankAccountResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ThirdPartyBankAccountResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseThirdPartyResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ThirdPartyResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseThirdPartyStatisticsResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `ThirdPartyStatisticsResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseTimesheetResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `TimesheetResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseTrainingBudgetResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `TrainingBudgetResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseTrainingRequestResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `TrainingRequestResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseTrainingResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `TrainingResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseTransactionResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `TransactionResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseTransactionTypeResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `TransactionTypeResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseUserAccountResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `UserAccountResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseUserRoleAssignmentResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `UserRoleAssignmentResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseVariantAttributeResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `VariantAttributeResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseVariantPriceResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `VariantPriceResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseVariantResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `VariantResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseVoid

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `?` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseWalletResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `WalletResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseWarehouseLayoutResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `WarehouseLayoutResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseWarehouseTransferResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `WarehouseTransferResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## ApiResponseWorkflowRequestResponse

| Field | Type | Required |
|---|---|---|
| `success` | `boolean` | no |
| `data` | `WorkflowRequestResponse` | no |
| `message` | `string` | no |
| `errorCode` | `string` | no |
| `timestamp` | `string(date-time)` | no |

## AppBusinessSettingsResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `negotiateSellingPrice` | `boolean` | no |
| `sellingPriceIncludeVat` | `boolean` | no |
| `authorizeExceptionalDiscount` | `boolean` | no |
| `grantableDiscountRate` | `number(double)` | no |
| `printLogo` | `boolean` | no |
| `paperFormat` | `string` | no |
| `lengthOfVatInvoiceNumber` | `integer(int32)` | no |
| `prefixOfVatInvoiceNumber` | `string` | no |
| `lowStockAlert` | `boolean` | no |
| `preventiveMaintenanceAlert` | `boolean` | no |
| `defaultCurrency` | `string` | no |
| `legalIdentity` | `string` | no |
| `taxIdentifier` | `string` | no |
| `requireSalesOrderApproval` | `boolean` | no |
| `requireReturnApproval` | `boolean` | no |

## ApplicationResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `jobOfferId` | `string(uuid)` | no |
| `candidatNom` | `string` | no |
| `candidatPrenom` | `string` | no |
| `candidatEmail` | `string` | no |
| `candidatTelephone` | `string` | no |
| `cvFileId` | `string(uuid)` | no |
| `lettreMotivationFileId` | `string(uuid)` | no |
| `status` | `string` | no |

## ApplyCommercialSubscriptionRequest

| Field | Type | Required |
|---|---|---|
| `planCode` | `string` | yes |
| `addOnCodes` | `string[]` | no |

## ApproveDocumentRequest

| Field | Type | Required |
|---|---|---|
| `expiresAt` | `string(date-time)` | no |
| `notes` | `string` | no |

## ArtistProfileResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `businessActorId` | `string(uuid)` | no |
| `ownerUserId` | `string(uuid)` | no |
| `slug` | `string` | no |
| `displayName` | `string` | no |
| `bannerFileId` | `string(uuid)` | no |
| `biography` | `string` | no |
| `location` | `string` | no |
| `status` | `string` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |

## ArtworkCommentResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `artworkId` | `string(uuid)` | no |
| `authorUserId` | `string(uuid)` | no |
| `content` | `string` | no |
| `createdAt` | `string(date-time)` | no |

## ArtworkInvoiceLinkResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `artworkId` | `string(uuid)` | no |
| `invoiceId` | `string(uuid)` | no |
| `invoiceStatus` | `string` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |

## ArtworkLikeResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `artworkId` | `string(uuid)` | no |
| `userId` | `string(uuid)` | no |
| `createdAt` | `string(date-time)` | no |

## ArtworkMediaResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `artworkId` | `string(uuid)` | no |
| `fileId` | `string(uuid)` | no |
| `kind` | `string` | no |
| `position` | `integer(int32)` | no |
| `createdAt` | `string(date-time)` | no |

## ArtworkProductLinkResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `artworkId` | `string(uuid)` | no |
| `productId` | `string(uuid)` | no |
| `commercializationStatus` | `string` | no |
| `createdAt` | `string(date-time)` | no |

## ArtworkResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `artistProfileId` | `string(uuid)` | no |
| `title` | `string` | no |
| `description` | `string` | no |
| `technique` | `string` | no |
| `style` | `string` | no |
| `dimensions` | `string` | no |
| `tags` | `string[]` | no |
| `status` | `string` | no |
| `publishedAt` | `string(date-time)` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |

## ArtworkSaleLinkResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `artworkId` | `string(uuid)` | no |
| `salesOrderId` | `string(uuid)` | no |
| `saleStatus` | `string` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |

## AssetPortfolioSnapshot

| Field | Type | Required |
|---|---|---|
| `scopeType` | `string` | no |
| `scopeId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `totalResources` | `integer(int32)` | no |
| `assignedResources` | `integer(int64)` | no |
| `reservedResources` | `integer(int64)` | no |
| `openMaintenanceCount` | `integer(int64)` | no |
| `countsByStatus` | `object` | no |
| `countsByCategory` | `object` | no |

## AssetPortfolioView

| Field | Type | Required |
|---|---|---|
| `scopeType` | `string` | no |
| `scopeId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `totalResources` | `integer(int32)` | no |
| `assignedResources` | `integer(int64)` | no |
| `reservedResources` | `integer(int64)` | no |
| `openMaintenanceCount` | `integer(int64)` | no |
| `countsByStatus` | `object` | no |
| `countsByCategory` | `object` | no |
| `resources` | `AssetResourceView[]` | no |

## AssetProfileResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `resourceId` | `string(uuid)` | no |
| `physicalSpaceId` | `string(uuid)` | no |
| `ownerActorId` | `string(uuid)` | no |
| `supplierThirdPartyId` | `string(uuid)` | no |
| `assetClass` | `string` | no |
| `criticality` | `string` | no |
| `lifecyclePhase` | `string` | no |
| `complianceStatus` | `string` | no |
| `acquisitionCost` | `number` | no |
| `currentValue` | `number` | no |
| `depreciationMethod` | `string` | no |
| `acquisitionDate` | `string(date-time)` | no |
| `warrantyUntil` | `string(date-time)` | no |
| `expectedRenewalDate` | `string(date-time)` | no |
| `lastComplianceCheckAt` | `string(date-time)` | no |
| `nextComplianceCheckAt` | `string(date-time)` | no |
| `maintenanceContractReference` | `string` | no |
| `notes` | `string` | no |

## AssetResourceView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `resourceCode` | `string` | no |
| `name` | `string` | no |
| `category` | `string` | no |
| `status` | `string` | no |
| `ipAddress` | `string` | no |
| `macAddress` | `string` | no |
| `assignment` | `TargetView` | no |
| `reservation` | `TargetView` | no |
| `maintenanceCount` | `integer(int32)` | no |
| `openMaintenanceCount` | `integer(int64)` | no |

## AssignAdministrativeRoleRequest

| Field | Type | Required |
|---|---|---|
| `roleId` | `string(uuid)` | yes |
| `scopeType` | `string` | no |
| `scopeId` | `string(uuid)` | no |
| `scope` | `string` | yes |

## AssignCashRegisterRequest

| Field | Type | Required |
|---|---|---|
| `cashierId` | `string(uuid)` | yes |

## AssignMaterialResourceRequest

| Field | Type | Required |
|---|---|---|
| `assigneeType` | `string` | yes |
| `assigneeId` | `string(uuid)` | yes |

## AssignOperationalResponsibilityRequest

| Field | Type | Required |
|---|---|---|
| `physicalSpaceId` | `string(uuid)` | no |
| `actorId` | `string(uuid)` | yes |
| `responsibilityType` | `string` | yes |
| `primaryResponsibility` | `boolean` | no |
| `active` | `boolean` | no |
| `notes` | `string` | no |

## AssignRoleToUserRequest

| Field | Type | Required |
|---|---|---|
| `userId` | `string(uuid)` | yes |
| `roleId` | `string(uuid)` | yes |
| `scopeType` | `string` | no |
| `scopeId` | `string(uuid)` | no |
| `scope` | `string` | yes |

## AttachArtworkMediaRequest

| Field | Type | Required |
|---|---|---|
| `fileId` | `string(uuid)` | yes |
| `kind` | `string` | yes |
| `position` | `integer(int32)` | no |

## AttachDocumentRequest

| Field | Type | Required |
|---|---|---|
| `targetType` | `string` | yes |
| `targetId` | `string(uuid)` | yes |
| `fileId` | `string(uuid)` | yes |
| `documentCategory` | `string` | yes |
| `label` | `string` | no |

## AttachmentView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `targetType` | `string` | no |
| `targetId` | `string(uuid)` | no |
| `filename` | `string` | no |
| `contentType` | `string` | no |
| `sizeBytes` | `integer(int64)` | no |
| `createdAt` | `string(date-time)` | no |

## AuditLogResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `module` | `string` | no |
| `action` | `string` | no |
| `entityType` | `string` | no |
| `entityId` | `string(uuid)` | no |
| `userId` | `string(uuid)` | no |
| `newValue` | `string` | no |
| `timestamp` | `string(date-time)` | no |
| `requestId` | `string` | no |
| `metadata` | `object` | no |

## AuditLogView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `action` | `string` | no |
| `targetType` | `string` | no |
| `targetId` | `string(uuid)` | no |
| `details` | `string` | no |
| `createdAt` | `string(date-time)` | no |

## AuditPage

| Field | Type | Required |
|---|---|---|
| `content` | `SystemAuditEntry[]` | no |
| `totalElements` | `integer(int64)` | no |
| `page` | `integer(int32)` | no |
| `size` | `integer(int32)` | no |
| `totalPages` | `integer(int32)` | no |

## AutoMatchResult

| Field | Type | Required |
|---|---|---|
| `linesProcessed` | `integer(int32)` | no |
| `matchesCreated` | `integer(int32)` | no |

## BalanceDesComptesDto

| Field | Type | Required |
|---|---|---|
| `totalDebitOuverture` | `number` | no |
| `totalCreditOuverture` | `number` | no |
| `totalDebitMouvement` | `number` | no |
| `totalCreditMouvement` | `number` | no |
| `totalDebitCloture` | `number` | no |
| `totalCreditCloture` | `number` | no |
| `lignes` | `LigneBalanceDto[]` | no |

## BalanceResponse

| Field | Type | Required |
|---|---|---|
| `balance` | `number` | no |

## BalanceSheet

| Field | Type | Required |
|---|---|---|
| `startDate` | `string(date)` | no |
| `endDate` | `string(date)` | no |
| `assets` | `ReportLineItem[]` | no |
| `liabilities` | `ReportLineItem[]` | no |
| `totalAssets` | `number` | no |
| `totalLiabilities` | `number` | no |

## BankAccount

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `thirdPartyId` | `string(uuid)` | no |
| `label` | `string` | no |
| `iban` | `string` | no |
| `swiftBic` | `string` | no |
| `bankName` | `string` | no |
| `currency` | `string` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |
| `primary` | `boolean` | no |

## BankAccountResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `bankThirdPartyId` | `string(uuid)` | no |
| `ownerThirdPartyId` | `string(uuid)` | no |
| `bankName` | `string` | no |
| `accountNumber` | `string` | no |
| `iban` | `string` | no |
| `currency` | `string` | no |
| `status` | `string` | no |

## BankCategoryRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `label` | `string` | yes |

## BankCategoryResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `code` | `string` | no |
| `label` | `string` | no |

## BankReconciliationView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `reconciliationReference` | `string` | no |
| `bankAccountNumber` | `string` | no |
| `matchedAmount` | `number` | no |
| `createdAt` | `string(date-time)` | no |

## BankResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `code` | `string` | no |
| `name` | `string` | no |
| `swiftCode` | `string` | no |
| `bankCode` | `string` | no |
| `country` | `string` | no |
| `address` | `string` | no |
| `bankCategoryId` | `string(uuid)` | no |
| `isActive` | `boolean` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |

## BankStatementPostingView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `statementReference` | `string` | no |
| `amount` | `number` | no |
| `currency` | `string` | no |
| `createdAt` | `string(date-time)` | no |

## BankStatementResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `bankAccountId` | `string(uuid)` | no |
| `statementNumber` | `string` | no |
| `statementDate` | `string(date)` | no |
| `openingBalance` | `number` | no |
| `closingBalance` | `number` | no |
| `status` | `string` | no |
| `closedAt` | `string(date-time)` | no |

## BankTransactionResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `bankAccountId` | `string(uuid)` | no |
| `statementId` | `string(uuid)` | no |
| `referenceNumber` | `string` | no |
| `transactionType` | `string` | no |
| `transactionDate` | `string(date)` | no |
| `amount` | `number` | no |
| `description` | `string` | no |
| `createdBy` | `string(uuid)` | no |
| `status` | `string` | no |
| `reconciledAt` | `string(date-time)` | no |
| `validatedAt` | `string(date-time)` | no |
| `validatedBy` | `string(uuid)` | no |
| `cancelledAt` | `string(date-time)` | no |
| `cancelledBy` | `string(uuid)` | no |
| `cancellationReason` | `string` | no |

## BankView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `code` | `string` | no |
| `name` | `string` | no |
| `active` | `boolean` | no |
| `createdAt` | `string(date-time)` | no |

## BatchResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `productId` | `string(uuid)` | no |
| `lotNumber` | `string` | no |
| `manufacturingDate` | `string(date)` | no |
| `expiryDate` | `string(date)` | no |
| `quantity` | `integer(int32)` | no |

## BatchStatementLinesRequest

| Field | Type | Required |
|---|---|---|
| `lines` | `CreateStatementLineRequest[]` | yes |

## BilanDto

| Field | Type | Required |
|---|---|---|
| `actifs` | `ReportItemDto[]` | no |
| `passifs` | `ReportItemDto[]` | no |
| `capitauxPropres` | `ReportItemDto[]` | no |

## BillView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `customerId` | `string(uuid)` | no |
| `reference` | `string` | no |
| `totalAmount` | `number` | no |
| `paidAmount` | `number` | no |
| `linkedServiceCode` | `string` | no |
| `linkedDocumentType` | `string` | no |
| `linkedDocumentId` | `string(uuid)` | no |
| `linkedSyncedAmount` | `number` | no |
| `linkedPendingSyncAmount` | `number` | no |
| `currency` | `string` | no |
| `status` | `string` | no |
| `createdAt` | `string(date-time)` | no |

## BillingInvoiceLineRequest

| Field | Type | Required |
|---|---|---|
| `productId` | `string(uuid)` | yes |
| `quantity` | `number` | yes |
| `unitPrice` | `number` | yes |
| `taxRate` | `number` | no |

## BlockResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `chainCode` | `string` | no |
| `height` | `integer(int64)` | no |
| `previousHash` | `string` | no |
| `merkleRoot` | `string` | no |
| `blockHash` | `string` | no |
| `nonce` | `integer(int64)` | no |
| `difficulty` | `integer(int32)` | no |
| `transactionCount` | `integer(int32)` | no |
| `minedBy` | `string` | no |
| `minedAt` | `string(date-time)` | no |

## BracketLine

| Field | Type | Required |
|---|---|---|
| `ordre` | `integer(int32)` | no |
| `lowerBound` | `number` | no |
| `upperBound` | `number` | no |
| `rate` | `number` | no |

## BrouillardComptableDto

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `type` | `string` | no |
| `statut` | `string` | no |
| `sourceId` | `string` | no |
| `sourceType` | `string` | no |
| `numeroPiece` | `string` | no |
| `datePiece` | `string(date)` | no |
| `libelle` | `string` | no |
| `montantTotal` | `number` | no |
| `devise` | `string` | no |
| `journalId` | `string(uuid)` | no |
| `journalCode` | `string` | no |
| `journalLibelle` | `string` | no |
| `periodeId` | `string(uuid)` | no |
| `periodeCode` | `string` | no |
| `ecritureId` | `string(uuid)` | no |
| `attachmentIds` | `string(uuid)[]` | no |
| `notes` | `string` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |
| `createdBy` | `string` | no |
| `validatedBy` | `string` | no |
| `validatedAt` | `string(date-time)` | no |
| `rejectedBy` | `string` | no |
| `rejectedAt` | `string(date-time)` | no |
| `rejectionReason` | `string` | no |

## BrouillardRejectionRequest

| Field | Type | Required |
|---|---|---|
| `reason` | `string` | no |

## BrouillardValidationRequest

| Field | Type | Required |
|---|---|---|
| `notes` | `string` | no |
| `forceValidation` | `boolean` | no |

## Budget

Type: `object`

## BudgetVsRealise

| Field | Type | Required |
|---|---|---|
| `fiscalYearId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `lines` | `BudgetVsRealiseLine[]` | no |
| `totalBudgeted` | `number` | no |
| `totalRealised` | `number` | no |
| `totalVariance` | `number` | no |

## BudgetVsRealiseLine

| Field | Type | Required |
|---|---|---|
| `accountId` | `string(uuid)` | no |
| `accountCode` | `string` | no |
| `accountLabel` | `string` | no |
| `budgeted` | `number` | no |
| `realised` | `number` | no |
| `variance` | `number` | no |

## BusinessActorRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | no |
| `isIndividual` | `boolean` | no |
| `isAvailable` | `boolean` | no |
| `isVerified` | `boolean` | no |
| `isActive` | `boolean` | no |
| `type` | `string` | no |
| `role` | `string` | no |
| `qualifications` | `string[]` | no |
| `paymentMethods` | `string[]` | no |
| `addresses` | `string(uuid)[]` | no |
| `biography` | `string` | no |
| `name` | `string` | yes |
| `businessId` | `string` | no |
| `niu` | `string` | no |
| `tradeRegistryNumber` | `string` | no |
| `website` | `string` | no |
| `contactPhone` | `string` | no |
| `privateAddress` | `string` | no |
| `businessAddress` | `string` | no |
| `businessProfile` | `string` | no |

## BusinessActorResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `actorId` | `string(uuid)` | no |
| `governanceStatus` | `string` | no |
| `governedByUserId` | `string(uuid)` | no |
| `governedAt` | `string(date-time)` | no |
| `governanceReason` | `string` | no |
| `code` | `string` | no |
| `isIndividual` | `boolean` | no |
| `isAvailable` | `boolean` | no |
| `isVerified` | `boolean` | no |
| `isActive` | `boolean` | no |
| `type` | `string` | no |
| `role` | `string` | no |
| `qualifications` | `string[]` | no |
| `paymentMethods` | `string[]` | no |
| `addresses` | `string(uuid)[]` | no |
| `biography` | `string` | no |
| `deletedAt` | `string(date-time)` | no |
| `name` | `string` | no |
| `businessId` | `string` | no |
| `niu` | `string` | no |
| `tradeRegistryNumber` | `string` | no |
| `website` | `string` | no |
| `contactPhone` | `string` | no |
| `privateAddress` | `string` | no |
| `businessAddress` | `string` | no |
| `businessProfile` | `string` | no |

## BusinessDomainResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `code` | `string` | no |
| `service` | `string` | no |
| `parentId` | `string(uuid)` | no |
| `name` | `string` | no |
| `imageUri` | `string` | no |
| `imageId` | `string(uuid)` | no |
| `type` | `string` | no |
| `typeLabel` | `string` | no |
| `description` | `string` | no |

## CalculateRequest

| Field | Type | Required |
|---|---|---|
| `employeeId` | `string(uuid)` | no |
| `originPeriod` | `string` | no |
| `newBaseSalary` | `number` | no |
| `targetPeriod` | `string` | no |
| `reason` | `string` | no |

## CampaignResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `warehouseId` | `string(uuid)` | no |
| `physicalSpaceId` | `string(uuid)` | no |
| `supervisorActorId` | `string(uuid)` | no |
| `campaignCode` | `string` | no |
| `campaignType` | `string` | no |
| `status` | `string` | no |
| `approvalRequired` | `boolean` | no |
| `scopeType` | `string` | no |
| `scheduledAt` | `string(date-time)` | no |
| `startedAt` | `string(date-time)` | no |
| `completedAt` | `string(date-time)` | no |
| `variancePercent` | `number` | no |
| `notes` | `string` | no |

## CampaignSummaryView

| Field | Type | Required |
|---|---|---|
| `totalCampaigns` | `integer(int32)` | no |
| `activeCampaigns` | `integer(int64)` | no |
| `pendingApprovalCampaigns` | `integer(int64)` | no |

## CaptchaChallengeResponse

| Field | Type | Required |
|---|---|---|
| `captchaToken` | `string` | no |
| `prompt` | `string` | no |
| `answerPreview` | `string` | no |
| `expiresInSeconds` | `integer(int64)` | no |

## CaptchaVerificationResponse

| Field | Type | Required |
|---|---|---|
| `captchaVerificationToken` | `string` | no |
| `expiresInSeconds` | `integer(int64)` | no |

## CapturePayVariableRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `period` | `string` | no |
| `overtimeHoursDay` | `number` | no |
| `overtimeHoursNight` | `number` | no |
| `overtimeHoursSundayHoliday` | `number` | no |
| `bonuses` | `number` | no |
| `unpaidAbsenceDays` | `number` | no |
| `advances` | `number` | no |
| `workedDaysOverride` | `integer(int32)` | no |

## CashAuditEntryView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `action` | `string` | no |
| `targetType` | `string` | no |
| `targetId` | `string(uuid)` | no |
| `details` | `string` | no |
| `createdAt` | `string(date-time)` | no |

## CashDashboardView

| Field | Type | Required |
|---|---|---|
| `registerCount` | `integer(int64)` | no |
| `activeSessionCount` | `integer(int64)` | no |
| `pendingFundRequestCount` | `integer(int64)` | no |
| `pendingBillCount` | `integer(int64)` | no |
| `globalBalance` | `number` | no |
| `movementCountToday` | `integer(int64)` | no |

## CashDocumentView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `type` | `string` | no |
| `targetId` | `string(uuid)` | no |
| `reference` | `string` | no |
| `createdAt` | `string(date-time)` | no |

## CashFlowDto

| Field | Type | Required |
|---|---|---|
| `operationnel` | `CashFlowItemDto[]` | no |
| `investissement` | `CashFlowItemDto[]` | no |
| `financement` | `CashFlowItemDto[]` | no |

## CashFlowItemDto

| Field | Type | Required |
|---|---|---|
| `code` | `string` | no |
| `description` | `string` | no |
| `amount` | `number` | no |
| `category` | `string` | no |

## CashFlowStatement

| Field | Type | Required |
|---|---|---|
| `startDate` | `string(date)` | no |
| `endDate` | `string(date)` | no |
| `inflows` | `number` | no |
| `outflows` | `number` | no |
| `netFlow` | `number` | no |

## CashMovementView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `sessionId` | `string(uuid)` | no |
| `registerId` | `string(uuid)` | no |
| `accountId` | `string(uuid)` | no |
| `type` | `string` | no |
| `amount` | `number` | no |
| `currency` | `string` | no |
| `reference` | `string` | no |
| `status` | `string` | no |
| `accountingPostingId` | `string(uuid)` | no |
| `accountingEntryType` | `string` | no |
| `registerAccountNumber` | `string` | no |
| `counterpartyAccountNumber` | `string` | no |
| `createdAt` | `string(date-time)` | no |

## CashNotificationView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `channel` | `string` | no |
| `subject` | `string` | no |
| `recipient` | `string` | no |
| `status` | `string` | no |
| `createdAt` | `string(date-time)` | no |

## CashReconciliationView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `sessionId` | `string(uuid)` | no |
| `registerId` | `string(uuid)` | no |
| `status` | `string` | no |
| `review` | `string` | no |
| `justification` | `string` | no |
| `createdAt` | `string(date-time)` | no |
| `reviewedAt` | `string(date-time)` | no |

## CashRegisterPostingView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `registerReference` | `string` | no |
| `registerId` | `string(uuid)` | no |
| `registerAccountId` | `string(uuid)` | no |
| `registerAccountNumber` | `string` | no |
| `amount` | `number` | no |
| `currency` | `string` | no |
| `postingType` | `string` | no |
| `sessionId` | `string(uuid)` | no |
| `movementId` | `string(uuid)` | no |
| `debitAccountNumber` | `string` | no |
| `creditAccountNumber` | `string` | no |
| `counterpartyAccountNumber` | `string` | no |
| `note` | `string` | no |
| `createdAt` | `string(date-time)` | no |

## CashRegisterView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `code` | `string` | no |
| `label` | `string` | no |
| `status` | `string` | no |
| `assignedCashierId` | `string(uuid)` | no |
| `accountingAccountId` | `string(uuid)` | no |
| `accountingAccountNumber` | `string` | no |
| `createdAt` | `string(date-time)` | no |

## CashReportView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `reportType` | `string` | no |
| `payload` | `object` | no |
| `generatedAt` | `string(date-time)` | no |

## CashierAssignmentView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `cashierId` | `string(uuid)` | no |
| `assignedAt` | `string(date-time)` | no |

## CashierLookupView

| Field | Type | Required |
|---|---|---|
| `organizations` | `KernelOrganizationView[]` | no |
| `agencies` | `KernelAgencyView[]` | no |
| `cashiers` | `CashierProfileView[]` | no |
| `accounts` | `WalletAccountView[]` | no |
| `customers` | `CounterpartyView[]` | no |

## CashierProfileView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `kernelUserId` | `string(uuid)` | no |
| `email` | `string` | no |
| `fullName` | `string` | no |
| `kind` | `string` | no |
| `active` | `boolean` | no |
| `createdAt` | `string(date-time)` | no |

## CashierSessionView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `registerId` | `string(uuid)` | no |
| `cashierId` | `string(uuid)` | no |
| `status` | `string` | no |
| `openingAmount` | `number` | no |
| `closingAmount` | `number` | no |
| `currency` | `string` | no |
| `openedAt` | `string(date-time)` | no |
| `closedAt` | `string(date-time)` | no |
| `locked` | `boolean` | no |

## CatalogServiceView

| Field | Type | Required |
|---|---|---|
| `code` | `string` | no |
| `displayName` | `string` | no |
| `description` | `string` | no |
| `nativeService` | `boolean` | no |
| `active` | `boolean` | no |

## CategoryI18nResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `categoryId` | `string(uuid)` | no |
| `locale` | `string` | no |
| `name` | `string` | no |
| `description` | `string` | no |

## CertificationResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `type` | `string` | no |
| `name` | `string` | no |
| `description` | `string` | no |
| `obtainmentDate` | `string(date-time)` | no |

## ChainValidationReport

| Field | Type | Required |
|---|---|---|
| `chainCode` | `string` | no |
| `valid` | `boolean` | no |
| `checkedBlocks` | `integer(int64)` | no |
| `checkedTransactions` | `integer(int64)` | no |
| `latestHash` | `string` | no |
| `errors` | `string[]` | no |

## ChangePasswordRequest

| Field | Type | Required |
|---|---|---|
| `currentPassword` | `string` | no |
| `newPassword` | `string` | no |

## CheckDepositItemResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `depositId` | `string(uuid)` | no |
| `checkId` | `string(uuid)` | no |
| `amount` | `number` | no |

## CheckDepositResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `bankAccountId` | `string(uuid)` | no |
| `reference` | `string` | no |
| `depositDate` | `string(date)` | no |
| `status` | `string` | no |
| `totalAmount` | `number` | no |
| `checkCount` | `integer(int32)` | no |
| `cashedDate` | `string(date)` | no |
| `bankTransactionId` | `string(uuid)` | no |
| `submittedAt` | `string(date-time)` | no |
| `validatedAt` | `string(date-time)` | no |
| `rejectedAt` | `string(date-time)` | no |
| `rejectionReason` | `string` | no |
| `items` | `CheckDepositItemResponse[]` | no |

## CheckPaymentResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `bankAccountId` | `string(uuid)` | no |
| `checkType` | `string` | no |
| `checkNumber` | `string` | no |
| `amount` | `number` | no |
| `amountInWords` | `string` | no |
| `currency` | `string` | no |
| `partnerName` | `string` | no |
| `partnerId` | `string(uuid)` | no |
| `status` | `string` | no |
| `issueDate` | `string(date)` | no |
| `dueDate` | `string(date)` | no |
| `receiptDate` | `string(date)` | no |
| `issuerBank` | `string` | no |
| `imageUrl` | `string` | no |
| `description` | `string` | no |
| `checkbookId` | `string(uuid)` | no |
| `bankTransactionId` | `string(uuid)` | no |
| `checkDepositId` | `string(uuid)` | no |
| `rejectionReason` | `string` | no |
| `cancellationReason` | `string` | no |

## CheckbookResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `bankAccountId` | `string(uuid)` | no |
| `prefix` | `string` | no |
| `type` | `string` | no |
| `isSystem` | `boolean` | no |
| `startNumber` | `integer(int64)` | no |
| `endNumber` | `integer(int64)` | no |
| `currentNumber` | `integer(int64)` | no |
| `availableChecks` | `integer(int64)` | no |
| `usedCount` | `integer(int64)` | no |
| `totalCapacity` | `integer(int64)` | no |
| `status` | `string` | no |
| `issuedAt` | `string(date)` | no |
| `closedAt` | `string(date-time)` | no |

## CheckbookStats

| Field | Type | Required |
|---|---|---|
| `checkbookId` | `string(uuid)` | no |
| `usedChecksCount` | `integer(int64)` | no |
| `totalAmountIssued` | `number` | no |
| `totalAmountCashed` | `number` | no |
| `remainingChecks` | `integer(int64)` | no |

## ClientApplicationPlanResponse

| Field | Type | Required |
|---|---|---|
| `code` | `string` | no |
| `displayName` | `string` | no |
| `description` | `string` | no |
| `allowedServices` | `string[]` | no |
| `systemDefault` | `boolean` | no |

## ClientApplicationResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `clientId` | `string` | no |
| `name` | `string` | no |
| `description` | `string` | no |
| `status` | `string` | no |
| `systemManaged` | `boolean` | no |
| `allowedServices` | `string[]` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |
| `lastAuthenticatedAt` | `string(date-time)` | no |
| `secretRotatedAt` | `string(date-time)` | no |

## CloneAdministrativeRoleRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `name` | `string` | yes |
| `scopeType` | `string` | no |

## CloseMaterialRequestRequest

| Field | Type | Required |
|---|---|---|
| `reason` | `string` | no |

## CloseSessionRequest

| Field | Type | Required |
|---|---|---|
| `closingAmount` | `number` | yes |
| `note` | `string` | no |

## ClosingRunView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `periodLabel` | `string` | no |
| `status` | `string` | no |
| `totalReceivables` | `number` | no |
| `totalPayables` | `number` | no |
| `startedAt` | `string(date-time)` | no |
| `completedAt` | `string(date-time)` | no |
| `blockingIssues` | `string[]` | no |

## CommentArtworkRequest

| Field | Type | Required |
|---|---|---|
| `content` | `string` | yes |

## CommercialAddOnCatalogResponse

| Field | Type | Required |
|---|---|---|
| `code` | `string` | no |
| `displayName` | `string` | no |
| `description` | `string` | no |
| `serviceCodes` | `string[]` | no |
| `compatiblePlanCodes` | `string[]` | no |
| `serviceQuotas` | `CommercialServiceQuotaResponse[]` | no |

## CommercialDocumentLineView

| Field | Type | Required |
|---|---|---|
| `productId` | `string(uuid)` | no |
| `product` | `ProductSummaryView` | no |
| `quantity` | `number` | no |
| `unitPrice` | `number` | no |
| `netAmount` | `number` | no |
| `taxRate` | `number` | no |
| `taxAmount` | `number` | no |
| `lineAmount` | `number` | no |

## CommercialDocumentView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `type` | `string` | no |
| `organizationId` | `string(uuid)` | no |
| `documentNumber` | `string` | no |
| `status` | `string` | no |
| `linkedAccountingInvoiceId` | `string(uuid)` | no |
| `linkedCashierBillId` | `string(uuid)` | no |
| `counterparty` | `CounterpartySummaryView` | no |
| `currency` | `string` | no |
| `lines` | `CommercialDocumentLineView[]` | no |
| `paymentSchedule` | `PaymentScheduleView[]` | no |
| `totalQuantity` | `number` | no |
| `subtotalAmount` | `number` | no |
| `totalTaxAmount` | `number` | no |
| `totalAmount` | `number` | no |
| `scheduledAmount` | `number` | no |
| `paidScheduledAmount` | `number` | no |
| `remainingScheduledAmount` | `number` | no |
| `createdAt` | `string(date-time)` | no |

## CommercialPlanCatalogResponse

| Field | Type | Required |
|---|---|---|
| `code` | `string` | no |
| `displayName` | `string` | no |
| `description` | `string` | no |
| `targetType` | `string` | no |
| `packCodes` | `string[]` | no |
| `serviceCodes` | `string[]` | no |
| `compatibleAddOnCodes` | `string[]` | no |
| `serviceQuotas` | `CommercialServiceQuotaResponse[]` | no |
| `systemDefault` | `boolean` | no |

## CommercialServiceQuota

| Field | Type | Required |
|---|---|---|
| `serviceCode` | `string` | no |
| `requestQuotaLimit` | `integer(int64)` | no |
| `requestQuotaWindowSeconds` | `integer(int64)` | no |

## CommercialServiceQuotaResponse

| Field | Type | Required |
|---|---|---|
| `serviceCode` | `string` | no |
| `requestQuotaLimit` | `integer(int64)` | no |
| `requestQuotaWindowSeconds` | `integer(int64)` | no |

## CommercialSubscriptionCatalogResponse

| Field | Type | Required |
|---|---|---|
| `plans` | `CommercialPlanCatalogResponse[]` | no |
| `addOns` | `CommercialAddOnCatalogResponse[]` | no |

## CommercializeArtworkRequest

| Field | Type | Required |
|---|---|---|
| `unitPrice` | `number` | yes |
| `currency` | `string` | yes |
| `familyCode` | `string` | no |
| `categoryCode` | `string` | no |

## CommissionAssetRequest

| Field | Type | Required |
|---|---|---|
| `physicalSpaceId` | `string(uuid)` | no |
| `ownerActorId` | `string(uuid)` | no |
| `supplierThirdPartyId` | `string(uuid)` | no |
| `assetClass` | `string` | yes |
| `criticality` | `string` | yes |
| `complianceStatus` | `string` | yes |
| `acquisitionCost` | `number` | no |
| `currentValue` | `number` | no |
| `depreciationMethod` | `string` | yes |
| `acquisitionDate` | `string(date-time)` | no |
| `warrantyUntil` | `string(date-time)` | no |
| `expectedRenewalDate` | `string(date-time)` | no |
| `lastComplianceCheckAt` | `string(date-time)` | no |
| `nextComplianceCheckAt` | `string(date-time)` | no |
| `maintenanceContractReference` | `string` | no |
| `notes` | `string` | no |

## CommissionSiteRequest

| Field | Type | Required |
|---|---|---|
| `siteCategory` | `string` | yes |
| `operatingModel` | `string` | yes |
| `cashEnabled` | `boolean` | no |
| `warehouseEnabled` | `boolean` | no |
| `maintenanceEnabled` | `boolean` | no |
| `inventoryEnabled` | `boolean` | no |
| `documentComplianceRequired` | `boolean` | no |
| `defaultPhysicalSpaceId` | `string(uuid)` | no |
| `readinessNotes` | `string` | no |

## CompleteEnrollmentRequest

| Field | Type | Required |
|---|---|---|
| `note` | `number` | no |
| `attestationId` | `string(uuid)` | no |

## CompleteInterviewRequest

| Field | Type | Required |
|---|---|---|
| `notes` | `string` | no |
| `resultat` | `string` | no |

## CompleteSynchronizationJobRequest

| Field | Type | Required |
|---|---|---|
| `summary` | `string` | yes |

## CompteResultatDto

| Field | Type | Required |
|---|---|---|
| `produits` | `ReportItemDto[]` | no |
| `charges` | `ReportItemDto[]` | no |

## ConfirmEmailVerificationRequest

| Field | Type | Required |
|---|---|---|
| `verificationToken` | `string` | yes |

## ConfirmMfaLoginRequest

| Field | Type | Required |
|---|---|---|
| `mfaToken` | `string` | yes |
| `code` | `string` | yes |

## ConfirmMfaRequest

| Field | Type | Required |
|---|---|---|
| `challengeToken` | `string` | yes |
| `code` | `string` | yes |

## ConnectorFieldResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `connectorTypeId` | `string(uuid)` | no |
| `fieldName` | `string` | no |
| `fieldLabel` | `string` | no |
| `fieldType` | `string` | no |
| `required` | `boolean` | no |
| `displayOrder` | `integer(int32)` | no |

## ContactResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `contactableType` | `string` | no |
| `contactableId` | `string(uuid)` | no |
| `firstName` | `string` | no |
| `lastName` | `string` | no |
| `title` | `string` | no |
| `isEmailVerified` | `boolean` | no |
| `isPhoneNumberVerified` | `boolean` | no |
| `isFavorite` | `boolean` | no |
| `phoneNumber` | `string` | no |
| `secondaryPhoneNumber` | `string` | no |
| `faxNumber` | `string` | no |
| `email` | `string` | no |
| `secondaryEmail` | `string` | no |
| `emailVerifiedAt` | `string(date-time)` | no |
| `phoneVerifiedAt` | `string(date-time)` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |
| `deletedAt` | `string(date-time)` | no |

## ContextualLoginResponse

| Field | Type | Required |
|---|---|---|
| `selectedTenantId` | `string(uuid)` | no |
| `selectedOrganizationId` | `string(uuid)` | no |
| `session` | `LoginResponse` | no |

## ContractResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `type` | `string` | no |
| `position` | `string` | no |
| `dateDebut` | `string(date)` | no |
| `dateFin` | `string(date)` | no |
| `salaireBase` | `number` | no |
| `avantagesNature` | `number` | no |
| `periodeEssai` | `integer(int32)` | no |
| `status` | `string` | no |
| `motifFin` | `string` | no |
| `documentFileId` | `string(uuid)` | no |

## ConvertApplicationRequest

| Field | Type | Required |
|---|---|---|
| `managerId` | `string(uuid)` | no |
| `numCnps` | `string` | no |
| `categorie` | `integer(int32)` | no |
| `echelon` | `string` | no |
| `dateEmbauche` | `string(date)` | no |
| `departmentCode` | `string` | no |
| `modePaiement` | `string` | no |
| `compteBancaire` | `string` | no |
| `numMobileMoney` | `string` | no |
| `operateurMm` | `string` | no |
| `contractType` | `string` | no |
| `position` | `string` | no |
| `contractDateDebut` | `string(date)` | no |
| `contractDateFin` | `string(date)` | no |
| `salaireBase` | `number` | no |
| `avantagesNature` | `number` | no |
| `periodeEssai` | `integer(int32)` | no |

## CounterpartRequest

| Field | Type | Required |
|---|---|---|
| `accountId` | `string(uuid)` | yes |
| `sense` | `string` | yes |
| `amountType` | `string` | yes |
| `ledgerId` | `string(uuid)` | no |
| `requiresThirdParty` | `boolean` | no |

## CounterpartySummaryView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `code` | `string` | no |
| `name` | `string` | no |
| `type` | `string` | no |
| `active` | `boolean` | no |

## CounterpartyView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `code` | `string` | no |
| `name` | `string` | no |
| `role` | `string` | no |
| `active` | `boolean` | no |

## CreateAccountGenerationRequest

| Field | Type | Required |
|---|---|---|
| `name` | `string` | yes |
| `type` | `string` | yes |
| `notes` | `string` | no |
| `externalId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |

## CreateAccountRequest

| Field | Type | Required |
|---|---|---|
| `accountNumber` | `string` | yes |
| `label` | `string` | yes |
| `accountType` | `string` | yes |
| `externalId` | `string(uuid)` | no |
| `notes` | `string` | no |

## CreateAccountingPeriodRequest

| Field | Type | Required |
|---|---|---|
| `fiscalYearId` | `string(uuid)` | yes |
| `startDate` | `string(date)` | yes |
| `endDate` | `string(date)` | yes |
| `organizationId` | `string(uuid)` | yes |

## CreateActionRequest

| Field | Type | Required |
|---|---|---|
| `entityId` | `string(uuid)` | no |
| `entityType` | `string` | no |
| `type` | `string` | no |
| `title` | `string` | no |
| `content` | `string` | no |
| `scheduledDate` | `string(date-time)` | no |
| `effect` | `string` | no |
| `notificationMethod` | `string` | no |
| `assignedUserId` | `string(uuid)` | no |

## CreateActorRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `firstName` | `string` | yes |
| `lastName` | `string` | yes |
| `name` | `string` | no |
| `phoneNumber` | `string` | no |
| `email` | `string(email)` | no |
| `description` | `string` | no |
| `type` | `string` | no |
| `gender` | `string` | no |
| `photoUri` | `string` | no |
| `photoId` | `string(uuid)` | no |
| `nationality` | `string` | no |
| `birthDate` | `string(date)` | no |
| `profession` | `string` | no |
| `biography` | `string` | no |
| `addresses` | `string(uuid)[]` | no |
| `contacts` | `string(uuid)[]` | no |

## CreateAddressRequest

| Field | Type | Required |
|---|---|---|
| `addressableType` | `string` | yes |
| `addressableId` | `string(uuid)` | yes |
| `type` | `string` | yes |
| `addressLine1` | `string` | yes |
| `addressLine2` | `string` | no |
| `city` | `string` | no |
| `state` | `string` | no |
| `locality` | `string` | no |
| `countryId` | `string(uuid)` | no |
| `zipCode` | `string` | no |
| `postalCode` | `string` | no |
| `poBox` | `string` | no |
| `isDefault` | `boolean` | no |
| `neighborhood` | `string` | no |
| `informalDescription` | `string` | no |
| `latitude` | `number(double)` | no |
| `longitude` | `number(double)` | no |

## CreateAdministrativeRoleRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `name` | `string` | yes |
| `scopeType` | `string` | no |
| `permissions` | `string[]` | no |

## CreateAgencyAffiliationRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `actorId` | `string(uuid)` | yes |
| `type` | `string` | no |
| `active` | `boolean` | no |

## CreateAgencyRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `ownerId` | `string(uuid)` | no |
| `managerId` | `string(uuid)` | no |
| `name` | `string` | yes |
| `location` | `string` | no |
| `description` | `string` | no |
| `transferable` | `boolean` | no |
| `active` | `boolean` | no |
| `logoUri` | `string` | no |
| `logoId` | `string(uuid)` | no |
| `shortName` | `string` | no |
| `longName` | `string` | no |
| `isIndividualBusiness` | `boolean` | no |
| `isHeadquarter` | `boolean` | no |
| `country` | `string` | no |
| `city` | `string` | no |
| `latitude` | `number(double)` | no |
| `longitude` | `number(double)` | no |
| `openTime` | `string` | no |
| `closeTime` | `string` | no |
| `phone` | `string` | no |
| `email` | `string` | no |
| `whatsapp` | `string` | no |
| `greetingMessage` | `string` | no |
| `averageRevenue` | `number` | no |
| `capitalShare` | `number` | no |
| `registrationNumber` | `string` | no |
| `socialNetwork` | `string` | no |
| `taxNumber` | `string` | no |
| `keywords` | `string[]` | no |
| `isPublic` | `boolean` | no |
| `isBusiness` | `boolean` | no |
| `totalAffiliatedCustomers` | `integer(int32)` | no |
| `agencyType` | `string` | no |

## CreateApplicationRequest

| Field | Type | Required |
|---|---|---|
| `jobOfferId` | `string(uuid)` | no |
| `candidatNom` | `string` | no |
| `candidatPrenom` | `string` | no |
| `candidatEmail` | `string` | no |
| `candidatTelephone` | `string` | no |
| `cvFileId` | `string(uuid)` | no |
| `lettreMotivationFileId` | `string(uuid)` | no |

## CreateArtistProfileRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `businessActorId` | `string(uuid)` | yes |
| `ownerUserId` | `string(uuid)` | yes |
| `slug` | `string` | yes |
| `displayName` | `string` | yes |
| `bannerFileId` | `string(uuid)` | no |
| `biography` | `string` | no |
| `location` | `string` | no |

## CreateArtworkRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `artistProfileId` | `string(uuid)` | yes |
| `title` | `string` | yes |
| `description` | `string` | no |
| `technique` | `string` | yes |
| `style` | `string` | yes |
| `dimensions` | `string` | no |
| `tags` | `string[]` | no |

## CreateAssignmentRequest

| Field | Type | Required |
|---|---|---|
| `cashierId` | `string(uuid)` | yes |
| `agencyId` | `string(uuid)` | yes |

## CreateAttachmentRequest

| Field | Type | Required |
|---|---|---|
| `targetType` | `string` | yes |
| `targetId` | `string(uuid)` | yes |
| `filename` | `string` | yes |
| `contentType` | `string` | yes |
| `sizeBytes` | `integer(int64)` | yes |

## CreateAuditEntryRequest

| Field | Type | Required |
|---|---|---|
| `action` | `string` | yes |
| `targetType` | `string` | yes |
| `targetId` | `string(uuid)` | no |
| `details` | `string` | no |

## CreateBankReconciliationRequest

| Field | Type | Required |
|---|---|---|
| `reconciliationReference` | `string` | yes |
| `bankAccountNumber` | `string` | yes |
| `matchedAmount` | `number` | yes |

## CreateBankRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `name` | `string` | yes |

## CreateBankStatementPostingRequest

| Field | Type | Required |
|---|---|---|
| `statementReference` | `string` | yes |
| `amount` | `number` | yes |
| `currency` | `string` | yes |

## CreateBatchRequest

| Field | Type | Required |
|---|---|---|
| `lotNumber` | `string` | yes |
| `manufacturingDate` | `string(date)` | no |
| `expiryDate` | `string(date)` | no |
| `quantity` | `integer(int32)` | yes |

## CreateBillRequest

| Field | Type | Required |
|---|---|---|
| `customerId` | `string(uuid)` | yes |
| `reference` | `string` | yes |
| `totalAmount` | `number` | yes |
| `currency` | `string` | yes |

## CreateBusinessDomainRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `service` | `string` | no |
| `parentId` | `string(uuid)` | no |
| `name` | `string` | yes |
| `imageUri` | `string` | no |
| `imageId` | `string(uuid)` | no |
| `type` | `string` | no |
| `typeLabel` | `string` | no |
| `description` | `string` | no |

## CreateCashRegisterPostingRequest

| Field | Type | Required |
|---|---|---|
| `registerReference` | `string` | yes |
| `registerId` | `string(uuid)` | no |
| `registerAccountId` | `string(uuid)` | no |
| `registerAccountNumber` | `string` | no |
| `amount` | `number` | yes |
| `currency` | `string` | yes |
| `postingType` | `string` | yes |
| `sessionId` | `string(uuid)` | no |
| `movementId` | `string(uuid)` | no |
| `counterpartyAccountNumber` | `string` | no |
| `note` | `string` | no |

## CreateCashRegisterRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `label` | `string` | yes |
| `agencyId` | `string(uuid)` | no |

## CreateCashierProfileRequest

| Field | Type | Required |
|---|---|---|
| `kernelUserId` | `string(uuid)` | no |
| `email` | `string(email)` | no |
| `fullName` | `string` | yes |
| `agencyId` | `string(uuid)` | no |
| `kind` | `string` | yes |

## CreateCertificationRequest

| Field | Type | Required |
|---|---|---|
| `type` | `string` | no |
| `name` | `string` | yes |
| `description` | `string` | no |
| `obtainmentDate` | `string(date-time)` | no |

## CreateCheckDepositRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `bankAccountId` | `string(uuid)` | yes |
| `reference` | `string` | yes |
| `depositDate` | `string(date)` | yes |

## CreateClientApplicationRequest

| Field | Type | Required |
|---|---|---|
| `clientId` | `string` | no |
| `name` | `string` | no |
| `description` | `string` | no |
| `clientSecret` | `string` | no |
| `planCode` | `string` | no |
| `allowedServices` | `string[]` | no |
| `requestQuotaLimit` | `integer(int64)` | no |
| `requestQuotaWindowSeconds` | `integer(int64)` | no |

## CreateCommercialDocumentRequest

| Field | Type | Required |
|---|---|---|
| `counterpartyThirdPartyId` | `string(uuid)` | yes |
| `documentNumber` | `string` | no |
| `currency` | `string` | yes |
| `lines` | `BillingInvoiceLineRequest[]` | yes |
| `paymentSchedule` | `PaymentScheduleRequest[]` | no |

## CreateCommercialThirdPartyRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `partyType` | `string` | yes |
| `partyId` | `string(uuid)` | yes |
| `code` | `string` | yes |
| `name` | `string` | yes |
| `accountingAccount` | `string` | no |
| `segment` | `string` | no |
| `qualificationScore` | `integer(int32)` | no |
| `enabled` | `boolean` | no |
| `prospect` | `boolean` | no |
| `type` | `string` | no |
| `legalForm` | `string` | no |
| `uniqueIdentificationNumber` | `string` | no |
| `tradeRegistrationNumber` | `string` | no |
| `acronym` | `string` | no |
| `longName` | `string` | no |
| `logoUri` | `string` | no |
| `logoId` | `string(uuid)` | no |
| `accountingAccountNumbers` | `string[]` | no |
| `authorizedPaymentMethods` | `string[]` | no |
| `authorizedCreditLimit` | `number` | no |
| `maxDiscountRate` | `number` | no |
| `vatSubject` | `boolean` | no |
| `operationsBalance` | `number` | no |
| `openingBalance` | `number` | no |
| `payTermNumber` | `integer(int32)` | no |
| `payTermType` | `string` | no |
| `thirdPartyFamily` | `string` | no |
| `classification` | `string` | no |
| `taxNumber` | `string` | no |

## CreateContactRequest

| Field | Type | Required |
|---|---|---|
| `contactableType` | `string` | yes |
| `contactableId` | `string(uuid)` | yes |
| `firstName` | `string` | no |
| `lastName` | `string` | no |
| `title` | `string` | no |
| `isEmailVerified` | `boolean` | no |
| `isPhoneNumberVerified` | `boolean` | no |
| `isFavorite` | `boolean` | no |
| `phoneNumber` | `string` | no |
| `secondaryPhoneNumber` | `string` | no |
| `faxNumber` | `string` | no |
| `email` | `string` | no |
| `secondaryEmail` | `string` | no |

## CreateCurrencyRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `label` | `string` | yes |
| `symbol` | `string` | yes |

## CreateDepartmentMemberRequest

| Field | Type | Required |
|---|---|---|
| `userId` | `string(uuid)` | yes |

## CreateDepartmentRequest

| Field | Type | Required |
|---|---|---|
| `agencyId` | `string(uuid)` | yes |
| `code` | `string` | yes |
| `name` | `string` | yes |
| `active` | `boolean` | no |

## CreateDraftEntryRequest

| Field | Type | Required |
|---|---|---|
| `journalId` | `string(uuid)` | yes |
| `reference` | `string` | yes |
| `entryDate` | `string(date-time)` | yes |
| `lines` | `EntryLineRequest[]` | yes |

## CreateEmployeeRequest

| Field | Type | Required |
|---|---|---|
| `actorId` | `string(uuid)` | no |
| `managerId` | `string(uuid)` | no |
| `numCnps` | `string` | no |
| `categorie` | `integer(int32)` | no |
| `echelon` | `string` | no |
| `dateEmbauche` | `string(date)` | no |
| `departmentCode` | `string` | no |
| `modePaiement` | `string` | no |
| `compteBancaire` | `string` | no |
| `numMobileMoney` | `string` | no |
| `operateurMm` | `string` | no |
| `contractType` | `string` | no |
| `position` | `string` | no |
| `contractDateDebut` | `string(date)` | no |
| `contractDateFin` | `string(date)` | no |
| `salaireBase` | `number` | no |
| `avantagesNature` | `number` | no |
| `periodeEssai` | `integer(int32)` | no |

## CreateEmployeeSkillRequest

| Field | Type | Required |
|---|---|---|
| `employeeId` | `string(uuid)` | no |
| `skillId` | `string(uuid)` | no |
| `niveauActuel` | `integer(int32)` | no |
| `niveauAttendu` | `integer(int32)` | no |
| `dateEvaluation` | `string(date)` | no |

## CreateEntryRequest

| Field | Type | Required |
|---|---|---|
| `journalId` | `string(uuid)` | yes |
| `reference` | `string` | yes |
| `entryDate` | `string(date-time)` | yes |
| `lines` | `EntryLineRequest[]` | yes |

## CreateExchangeRateRequest

| Field | Type | Required |
|---|---|---|
| `sourceCurrency` | `string` | yes |
| `targetCurrency` | `string` | yes |
| `rate` | `number` | yes |
| `rateDate` | `string(date)` | yes |

## CreateExpenseReportRequest

| Field | Type | Required |
|---|---|---|
| `employeeId` | `string(uuid)` | no |
| `periode` | `string` | no |
| `motif` | `string` | no |
| `missionOrderId` | `string(uuid)` | no |

## CreateFiscalYearRequest

| Field | Type | Required |
|---|---|---|
| `label` | `string` | yes |
| `startDate` | `string(date)` | yes |
| `endDate` | `string(date)` | yes |

## CreateFixedAssetRequest

| Field | Type | Required |
|---|---|---|
| `reference` | `string` | yes |
| `label` | `string` | yes |
| `acquisitionCost` | `number` | yes |
| `usefulLifeMonths` | `integer(int32)` | yes |
| `acquiredAt` | `string(date-time)` | no |

## CreateFundRequest

| Field | Type | Required |
|---|---|---|
| `registerId` | `string(uuid)` | yes |
| `cashierId` | `string(uuid)` | yes |
| `amount` | `number` | yes |
| `reason` | `string` | no |

## CreateGalleryEventRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `artistProfileId` | `string(uuid)` | yes |
| `name` | `string` | yes |
| `description` | `string` | no |
| `posterFileId` | `string(uuid)` | no |
| `startAt` | `string(date-time)` | yes |
| `endAt` | `string(date-time)` | yes |
| `location` | `string` | yes |
| `type` | `string` | yes |
| `maxCapacity` | `integer(int32)` | no |
| `ticketPrice` | `number` | no |

## CreateInventorySessionRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `agencyId` | `string(uuid)` | yes |
| `productId` | `string(uuid)` | yes |
| `referenceNumber` | `string` | no |
| `countedQuantity` | `number` | yes |

## CreateInvoiceAccountingRequest

| Field | Type | Required |
|---|---|---|
| `invoiceId` | `string(uuid)` | yes |
| `accountingStatus` | `string` | yes |

## CreateInvoiceLineRequest

| Field | Type | Required |
|---|---|---|
| `productId` | `string(uuid)` | yes |
| `quantity` | `number` | yes |
| `unitPrice` | `number` | yes |

## CreateInvoiceRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `customerThirdPartyId` | `string(uuid)` | yes |
| `orderId` | `string(uuid)` | no |
| `productId` | `string(uuid)` | no |
| `invoiceNumber` | `string` | no |
| `quantity` | `number` | no |
| `unitPrice` | `number` | no |
| `lines` | `CreateInvoiceLineRequest[]` | no |
| `currency` | `string` | yes |

## CreateInvoiceUploadRequest

| Field | Type | Required |
|---|---|---|
| `filename` | `string` | yes |
| `contentType` | `string` | yes |
| `sizeBytes` | `integer(int64)` | yes |

## CreateJobOfferRequest

| Field | Type | Required |
|---|---|---|
| `agencyId` | `string(uuid)` | no |
| `poste` | `string` | no |
| `departement` | `string` | no |
| `localisation` | `string` | no |
| `competencesRequises` | `string` | no |
| `dateLimite` | `string(date)` | no |
| `packageSalarial` | `string` | no |

## CreateJournalRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `label` | `string` | yes |
| `type` | `string` | yes |

## CreateLedgerRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `label` | `string` | yes |
| `type` | `string` | yes |
| `organizationId` | `string(uuid)` | yes |

## CreateLetteringRequest

| Field | Type | Required |
|---|---|---|
| `debitEntryId` | `string(uuid)` | yes |
| `creditEntryId` | `string(uuid)` | yes |
| `matchedAmount` | `number` | yes |

## CreateLookupTableRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | no |
| `label` | `string` | no |
| `countryCode` | `string` | no |
| `effectiveFrom` | `string(date)` | no |
| `effectiveTo` | `string(date)` | no |
| `entries` | `EntryLine[]` | no |

## CreateMaterialRequestRequest

| Field | Type | Required |
|---|---|---|
| `departmentId` | `string(uuid)` | yes |
| `reasonCode` | `string` | no |
| `reasonText` | `string` | no |
| `expectedReturnAt` | `string(date-time)` | no |
| `items` | `MaterialRequestItemInput[]` | yes |

## CreateMediaAssetRequest

| Field | Type | Required |
|---|---|---|
| `targetType` | `string` | yes |
| `targetId` | `string(uuid)` | yes |
| `fileId` | `string(uuid)` | yes |
| `mimeType` | `string` | yes |
| `position` | `integer(int32)` | no |
| `altText` | `string` | no |

## CreateMedicalCertificateRequest

| Field | Type | Required |
|---|---|---|
| `employeeId` | `string(uuid)` | no |
| `typeCertificat` | `string` | no |
| `dateEmission` | `string(date)` | no |
| `dateExpiration` | `string(date)` | no |
| `statut` | `string` | no |
| `fichierId` | `string(uuid)` | no |

## CreateMedicalVisitRequest

| Field | Type | Required |
|---|---|---|
| `employeeId` | `string(uuid)` | no |
| `dateVisite` | `string(date)` | no |
| `medecin` | `string` | no |
| `resultatAptitude` | `string` | no |
| `restrictions` | `string` | no |
| `prochaineEcheance` | `string(date)` | no |
| `certificatFileId` | `string(uuid)` | no |

## CreateMissionOrderRequest

| Field | Type | Required |
|---|---|---|
| `employeeId` | `string(uuid)` | no |
| `destination` | `string` | no |
| `objet` | `string` | no |
| `dateDebut` | `string(date)` | no |
| `dateFin` | `string(date)` | no |
| `montantAvance` | `number` | no |
| `centreCout` | `string` | no |

## CreateMovementRequest

| Field | Type | Required |
|---|---|---|
| `sessionId` | `string(uuid)` | no |
| `registerId` | `string(uuid)` | no |
| `accountId` | `string(uuid)` | no |
| `type` | `string` | yes |
| `amount` | `number` | yes |
| `currency` | `string` | yes |
| `reference` | `string` | yes |
| `counterpartyActorId` | `string(uuid)` | no |
| `counterpartyThirdPartyId` | `string(uuid)` | no |
| `note` | `string` | no |

## CreateNotificationRequest

| Field | Type | Required |
|---|---|---|
| `channel` | `string` | yes |
| `subject` | `string` | yes |
| `recipient` | `string` | yes |

## CreateOnboardingTaskRequest

| Field | Type | Required |
|---|---|---|
| `employeeId` | `string(uuid)` | no |
| `titre` | `string` | no |
| `description` | `string` | no |
| `assignedToPartyId` | `string(uuid)` | no |
| `echeance` | `string(date)` | no |

## CreateOperationRequest

| Field | Type | Required |
|---|---|---|
| `operationType` | `string` | yes |
| `reference` | `string` | yes |
| `amount` | `number` | yes |
| `currency` | `string` | yes |

## CreateOperationTemplateRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `code` | `string` | yes |
| `label` | `string` | yes |
| `operationType` | `string` | yes |
| `defaultLedgerId` | `string(uuid)` | yes |
| `mainAccountId` | `string(uuid)` | yes |
| `staticMainAccount` | `boolean` | no |
| `mainAccountSense` | `string` | yes |
| `mainAmountType` | `string` | yes |
| `customerCreditCeiling` | `number` | no |
| `counterparts` | `CounterpartRequest[]` | no |

## CreateOrganizationRequest

| Field | Type | Required |
|---|---|---|
| `businessActorId` | `string(uuid)` | yes |
| `code` | `string` | yes |
| `service` | `string` | yes |
| `isIndividualBusiness` | `boolean` | no |
| `email` | `string(email)` | no |
| `shortName` | `string` | yes |
| `longName` | `string` | yes |
| `description` | `string` | no |
| `logoUri` | `string` | no |
| `logoId` | `string(uuid)` | no |
| `websiteUrl` | `string` | no |
| `socialNetwork` | `string` | no |
| `businessRegistrationNumber` | `string` | no |
| `taxNumber` | `string` | no |
| `capitalShare` | `number` | no |
| `ceoName` | `string` | no |
| `yearFounded` | `integer(int32)` | no |
| `keywords` | `string[]` | no |
| `numberOfEmployees` | `integer(int32)` | no |
| `legalForm` | `string` | no |
| `isActive` | `boolean` | no |
| `status` | `string` | no |

## CreatePayElementRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | no |
| `label` | `string` | no |
| `category` | `string` | no |
| `method` | `string` | no |
| `baseReference` | `string` | no |
| `rate` | `number` | no |
| `ceiling` | `number` | no |
| `floor` | `number` | no |
| `exemptionThreshold` | `number` | no |
| `flatAmount` | `number` | no |
| `bracketTableCode` | `string` | no |
| `lookupTableCode` | `string` | no |
| `taxable` | `boolean` | no |
| `socialContributable` | `boolean` | no |
| `countryCode` | `string` | no |
| `displayOrder` | `integer(int32)` | no |
| `effectiveFrom` | `string(date)` | no |
| `effectiveTo` | `string(date)` | no |

## CreatePaymentRequest

| Field | Type | Required |
|---|---|---|
| `billingDocumentId` | `string(uuid)` | no |
| `invoiceId` | `string(uuid)` | no |
| `supplierInvoiceId` | `string(uuid)` | no |
| `counterpartyThirdPartyId` | `string(uuid)` | no |
| `reference` | `string` | yes |
| `amount` | `number` | yes |
| `currency` | `string` | yes |
| `paidAt` | `string(date-time)` | no |

## CreatePeriodRequest

| Field | Type | Required |
|---|---|---|
| `fiscalYearId` | `string(uuid)` | yes |
| `code` | `string` | yes |
| `startDate` | `string(date)` | yes |
| `endDate` | `string(date)` | yes |

## CreatePhysicalSpaceRequest

| Field | Type | Required |
|---|---|---|
| `parentSpaceId` | `string(uuid)` | no |
| `code` | `string` | yes |
| `name` | `string` | yes |
| `spaceType` | `string` | yes |
| `description` | `string` | no |
| `levelNumber` | `integer(int32)` | no |
| `capacity` | `integer(int32)` | no |
| `active` | `boolean` | no |

## CreatePlanAccountRequest

| Field | Type | Required |
|---|---|---|
| `accountNumber` | `string` | yes |
| `label` | `string` | yes |
| `accountClass` | `string` | yes |

## CreatePointOfInterestRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `agencyId` | `string(uuid)` | yes |
| `name` | `string` | yes |
| `poiType` | `string` | yes |
| `latitude` | `number(double)` | no |
| `longitude` | `number(double)` | no |

## CreatePointingRequest

| Field | Type | Required |
|---|---|---|
| `accountId` | `string(uuid)` | yes |
| `entryId` | `string(uuid)` | yes |
| `notes` | `string` | yes |

## CreateProductCategoryRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `code` | `string` | yes |
| `name` | `string` | yes |
| `parentCode` | `string` | no |
| `description` | `string` | no |

## CreateProductRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `sku` | `string` | yes |
| `name` | `string` | yes |
| `familyCode` | `string` | yes |
| `categoryCode` | `string` | no |
| `variantLabel` | `string` | yes |
| `barcode` | `string` | no |
| `description` | `string` | no |
| `minStockLevel` | `integer(int32)` | no |
| `maxStockLevel` | `integer(int32)` | no |
| `unitPrice` | `number` | yes |
| `currency` | `string` | yes |
| `status` | `string` | no |
| `cost` | `number` | no |
| `photo` | `string` | no |
| `uom` | `string` | no |
| `quantity` | `number` | no |
| `allowedSaleSizes` | `SaleSize[]` | no |

## CreateProposedActivityRequest

| Field | Type | Required |
|---|---|---|
| `type` | `string` | no |
| `name` | `string` | yes |
| `rate` | `number` | no |
| `description` | `string` | no |

## CreateRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `type` | `string` | no |
| `beneficiary` | `string` | no |
| `reference` | `string` | no |
| `totalAmount` | `number` | no |
| `monthlyAmount` | `number` | no |

## CreateReviewRequest

| Field | Type | Required |
|---|---|---|
| `employeeId` | `string(uuid)` | no |
| `evaluateurPartyId` | `string(uuid)` | no |
| `evaluateurDisplayName` | `string` | no |
| `periode` | `string` | no |

## CreateRhKpiSnapshotRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `periode` | `string` | no |
| `effectifTotal` | `integer(int32)` | no |
| `effectifActif` | `integer(int32)` | no |
| `tauxTurnover` | `number` | no |
| `tauxAbsenteisme` | `number` | no |
| `masseSalariale` | `number` | no |
| `couvertureCompetences` | `number` | no |

## CreateRoleRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `name` | `string` | yes |
| `scopeType` | `string` | no |
| `permissions` | `string[]` | no |

## CreateSalesOrderLineRequest

| Field | Type | Required |
|---|---|---|
| `productId` | `string(uuid)` | yes |
| `quantity` | `number` | yes |
| `unitPrice` | `number` | yes |

## CreateSalesOrderRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `customerThirdPartyId` | `string(uuid)` | no |
| `productId` | `string(uuid)` | no |
| `orderNumber` | `string` | no |
| `quantity` | `number` | no |
| `unitPrice` | `number` | no |
| `currency` | `string` | yes |
| `lines` | `CreateSalesOrderLineRequest[]` | no |

## CreateSessionRequest

| Field | Type | Required |
|---|---|---|
| `registerId` | `string(uuid)` | yes |
| `cashierId` | `string(uuid)` | yes |
| `openingAmount` | `number` | yes |
| `currency` | `string` | yes |

## CreateSkillRequest

| Field | Type | Required |
|---|---|---|
| `name` | `string` | yes |
| `categorie` | `string` | no |
| `description` | `string` | no |

## CreateSocialDeclarationRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `type` | `string` | no |
| `periode` | `string` | no |
| `format` | `string` | no |

## CreateStatementLineRequest

| Field | Type | Required |
|---|---|---|
| `statementId` | `string(uuid)` | yes |
| `reference` | `string` | yes |
| `amount` | `number` | yes |
| `currency` | `string` | yes |
| `direction` | `string` | yes |

## CreateStockMovementPostingRequest

| Field | Type | Required |
|---|---|---|
| `movementReference` | `string` | yes |
| `movementType` | `string` | yes |
| `valuationAmount` | `number` | yes |
| `currency` | `string` | yes |

## CreateTaxBracketTableRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | no |
| `label` | `string` | no |
| `countryCode` | `string` | no |
| `effectiveFrom` | `string(date)` | no |
| `effectiveTo` | `string(date)` | no |
| `brackets` | `BracketLine[]` | no |

## CreateTaxDeclarationRequest

| Field | Type | Required |
|---|---|---|
| `taxType` | `string` | yes |
| `periodLabel` | `string` | yes |
| `rate` | `number` | yes |

## CreateTaxDefinitionRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `label` | `string` | yes |
| `rate` | `number` | yes |

## CreateThirdPartyRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `partyType` | `string` | yes |
| `partyId` | `string(uuid)` | yes |
| `code` | `string` | yes |
| `name` | `string` | yes |
| `roles` | `string[]` | yes |
| `accountingAccount` | `string` | no |
| `segment` | `string` | no |
| `qualificationScore` | `integer(int32)` | no |
| `enabled` | `boolean` | no |
| `prospect` | `boolean` | no |
| `type` | `string` | no |
| `legalForm` | `string` | no |
| `uniqueIdentificationNumber` | `string` | no |
| `tradeRegistrationNumber` | `string` | no |
| `acronym` | `string` | no |
| `longName` | `string` | no |
| `logoUri` | `string` | no |
| `logoId` | `string(uuid)` | no |
| `accountingAccountNumbers` | `string[]` | no |
| `authorizedPaymentMethods` | `string[]` | no |
| `authorizedCreditLimit` | `number` | no |
| `maxDiscountRate` | `number` | no |
| `vatSubject` | `boolean` | no |
| `operationsBalance` | `number` | no |
| `openingBalance` | `number` | no |
| `payTermNumber` | `integer(int32)` | no |
| `payTermType` | `string` | no |
| `thirdPartyFamily` | `string` | no |
| `classification` | `string` | no |
| `taxNumber` | `string` | no |

## CreateTimesheetRequest

| Field | Type | Required |
|---|---|---|
| `employeeId` | `string(uuid)` | no |
| `periode` | `string` | no |
| `heuresNormales` | `number` | no |
| `heuresSupplementaires` | `number` | no |
| `heuresNuit` | `number` | no |
| `heuresWeekend` | `number` | no |
| `absencesNonJustifiees` | `number` | no |

## CreateTrainingBudgetRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `annee` | `integer(int32)` | no |
| `montantAlloue` | `number` | no |

## CreateTransactionTypeRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `label` | `string` | yes |
| `inbound` | `boolean` | no |

## CreateVariantRequest

| Field | Type | Required |
|---|---|---|
| `sku` | `string` | yes |
| `barcode` | `string` | no |
| `label` | `string` | yes |
| `isDefault` | `boolean` | no |
| `status` | `string` | no |

## CreateWalletRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `label` | `string` | no |

## CreateWarehouseTransferRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `sourceAgencyId` | `string(uuid)` | yes |
| `targetAgencyId` | `string(uuid)` | yes |
| `productId` | `string(uuid)` | yes |
| `referenceNumber` | `string` | no |
| `quantity` | `number` | yes |

## CreateWorkflowRequest

| Field | Type | Required |
|---|---|---|
| `type` | `string` | yes |
| `payload` | `JsonNode` | no |

## CrmAction

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `entityId` | `string(uuid)` | no |
| `entityType` | `string` | no |
| `type` | `string` | no |
| `title` | `string` | no |
| `content` | `string` | no |
| `scheduledDate` | `string(date-time)` | no |
| `status` | `string` | no |
| `effect` | `string` | no |
| `notificationMethod` | `string` | no |
| `assignedUserId` | `string(uuid)` | no |
| `createdAt` | `string(date-time)` | no |
| `completedAt` | `string(date-time)` | no |

## CsvColumnSpec

| Field | Type | Required |
|---|---|---|
| `header` | `string` | no |
| `required` | `boolean` | no |
| `example` | `string` | no |
| `acceptedValues` | `string[]` | no |

## CsvImportReport

| Field | Type | Required |
|---|---|---|
| `total` | `integer(int32)` | no |
| `created` | `integer(int32)` | no |
| `updated` | `integer(int32)` | no |
| `errors` | `CsvRowError[]` | no |

## CsvImportRequest

| Field | Type | Required |
|---|---|---|
| `csv` | `string` | no |

## CsvRowError

| Field | Type | Required |
|---|---|---|
| `line` | `integer(int32)` | no |
| `matricule` | `string` | no |
| `message` | `string` | no |

## CsvTemplateResponse

| Field | Type | Required |
|---|---|---|
| `csv` | `string` | no |
| `columns` | `CsvColumnSpec[]` | no |

## Currency

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `code` | `string` | no |
| `name` | `string` | no |
| `symbol` | `string` | no |
| `active` | `boolean` | no |

## CurrencyView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `code` | `string` | no |
| `label` | `string` | no |
| `symbol` | `string` | no |
| `active` | `boolean` | no |
| `createdAt` | `string(date-time)` | no |

## CustomerStats

| Field | Type | Required |
|---|---|---|
| `segment` | `string` | no |
| `ohadaType` | `string` | no |
| `vatSubject` | `boolean` | no |
| `retailSale` | `boolean` | no |
| `wholesale` | `boolean` | no |
| `superWholesale` | `boolean` | no |
| `semiWholesale` | `boolean` | no |

## DataSourceMode

| Field | Type | Required |
|---|---|---|
| `code` | `string` | no |
| `label` | `string` | no |
| `description` | `string` | no |

## DataSourceRequest

| Field | Type | Required |
|---|---|---|
| `source` | `string` | no |

## DataSourceResponse

| Field | Type | Required |
|---|---|---|
| `source` | `string` | no |

## DeactivateRequest

| Field | Type | Required |
|---|---|---|
| `departureDate` | `string(date)` | no |

## DeclarationResponse

| Field | Type | Required |
|---|---|---|
| `type` | `string` | no |
| `periode` | `string` | no |
| `employeeCount` | `integer(int32)` | no |
| `totalGrossBase` | `number` | no |
| `totalEmployee` | `number` | no |
| `totalEmployer` | `number` | no |
| `grandTotal` | `number` | no |
| `items` | `LineResponse[]` | no |

## DeclineMissionOrderRequest

| Field | Type | Required |
|---|---|---|
| `reason` | `string` | yes |

## DefineProductPriceRequest

| Field | Type | Required |
|---|---|---|
| `priceType` | `string` | yes |
| `amount` | `number` | yes |
| `currency` | `string` | yes |
| `effectiveFrom` | `string(date-time)` | no |

## DefineVariantPriceRequest

| Field | Type | Required |
|---|---|---|
| `priceType` | `string` | yes |
| `amount` | `number` | yes |
| `currency` | `string` | yes |
| `effectiveFrom` | `string(date-time)` | no |

## DeleteAssignmentRequest

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | yes |

## DenominationView

| Field | Type | Required |
|---|---|---|
| `currency` | `string` | no |
| `value` | `number` | no |
| `quantity` | `integer(int32)` | no |

## DepartmentMemberResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `departmentId` | `string(uuid)` | no |
| `userId` | `string(uuid)` | no |
| `updatedAt` | `string(date-time)` | no |

## DepartmentResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `code` | `string` | no |
| `name` | `string` | no |
| `active` | `boolean` | no |
| `updatedAt` | `string(date-time)` | no |

## DependentResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `nom` | `string` | no |
| `prenom` | `string` | no |
| `dateNaissance` | `string(date)` | no |
| `lienParente` | `string` | no |
| `certificatFileId` | `string(uuid)` | no |

## DepreciateFixedAssetRequest

| Field | Type | Required |
|---|---|---|
| `months` | `integer(int32)` | yes |

## DiscoverLoginContextsResponse

| Field | Type | Required |
|---|---|---|
| `selectionToken` | `string` | no |
| `expiresInSeconds` | `integer(int64)` | no |
| `contexts` | `DiscoveredLoginContextResponse[]` | no |

## DiscoverSignUpContextsRequest

| Field | Type | Required |
|---|---|---|
| `organizationCode` | `string` | yes |

## DiscoverSignUpContextsResponse

| Field | Type | Required |
|---|---|---|
| `selectionToken` | `string` | no |
| `expiresInSeconds` | `integer(int64)` | no |
| `contexts` | `SelectableSignUpContextResponse[]` | no |

## DiscoveredLoginContextResponse

| Field | Type | Required |
|---|---|---|
| `contextId` | `string` | no |
| `tenantId` | `string(uuid)` | no |
| `userId` | `string(uuid)` | no |
| `actorId` | `string(uuid)` | no |
| `organizations` | `UserOrganizationAccessResponse[]` | no |

## DocumentAnalysisResponse

| Field | Type | Required |
|---|---|---|
| `documentType` | `string` | yes |
| `documentNumber` | `string` | no |
| `issuingCountry` | `string` | yes |
| `holderName` | `string` | yes |
| `dateOfBirth` | `string(date)` | no |
| `issueDate` | `string(date)` | no |
| `expirationDate` | `string(date)` | no |
| `isValid` | `boolean` | yes |
| `validationMessage` | `string` | no |
| `confidenceScore` | `number(double)` | no |
| `hasUncertainty` | `boolean` | no |
| `additionalFields` | `object` | no |
| `rawExtractedText` | `string` | no |

## DocumentGovernanceOverview

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `policyCount` | `integer(int32)` | no |
| `documentCount` | `integer(int32)` | no |
| `approvalRequiredPolicyCount` | `integer(int64)` | no |
| `approvedDocuments` | `integer(int64)` | no |
| `rejectedDocuments` | `integer(int64)` | no |
| `expiredDocuments` | `integer(int64)` | no |
| `pendingDocuments` | `integer(int64)` | no |

## DocumentGovernanceOverviewSnapshot

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `policyCount` | `integer(int32)` | no |
| `documentCount` | `integer(int32)` | no |
| `approvalRequiredPolicyCount` | `integer(int64)` | no |
| `approvedDocuments` | `integer(int64)` | no |
| `rejectedDocuments` | `integer(int64)` | no |
| `expiredDocuments` | `integer(int64)` | no |
| `pendingDocuments` | `integer(int64)` | no |

## DocumentHubOverview

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `totalDocuments` | `integer(int32)` | no |
| `countsByTargetType` | `object` | no |
| `countsByCategory` | `object` | no |

## DocumentHubOverviewSnapshot

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `totalDocuments` | `integer(int32)` | no |
| `countsByTargetType` | `object` | no |
| `countsByCategory` | `object` | no |

## DocumentLinkView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `targetType` | `string` | no |
| `targetId` | `string(uuid)` | no |
| `fileId` | `string(uuid)` | no |
| `fileName` | `string` | no |
| `contentType` | `string` | no |
| `fileSize` | `integer(int64)` | no |
| `documentCategory` | `string` | no |
| `label` | `string` | no |
| `attachedByUserId` | `string(uuid)` | no |
| `attachedAt` | `string(date-time)` | no |

## DocumentResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `type` | `string` | no |
| `subjectId` | `string(uuid)` | no |
| `periode` | `string` | no |
| `fileId` | `string(uuid)` | no |
| `fileName` | `string` | no |
| `algorithm` | `string` | no |
| `contentHashHex` | `string` | no |
| `verificationCode` | `string` | no |
| `keyId` | `string` | no |
| `signedAt` | `string(date-time)` | no |

## DocumentReviewResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `documentLinkId` | `string(uuid)` | no |
| `reviewerUserId` | `string(uuid)` | no |
| `reviewStatus` | `string` | no |
| `reviewedAt` | `string(date-time)` | no |
| `expiresAt` | `string(date-time)` | no |
| `notes` | `string` | no |

## DocumentSequenceResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `documentType` | `string` | no |
| `prefix` | `string` | no |
| `suffix` | `string` | no |
| `paddingWidth` | `integer(int32)` | no |
| `nextNumber` | `integer(int64)` | no |

## DocumentSequenceSummaryView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `documentType` | `string` | no |
| `prefix` | `string` | no |
| `suffix` | `string` | no |
| `paddingWidth` | `integer(int32)` | no |
| `nextNumber` | `integer(int64)` | no |

## DocumentStatusView

| Field | Type | Required |
|---|---|---|
| `documentLinkId` | `string(uuid)` | no |
| `targetType` | `string` | no |
| `targetId` | `string(uuid)` | no |
| `documentCategory` | `string` | no |
| `mandatory` | `boolean` | no |
| `approvalRequired` | `boolean` | no |
| `reviewStatus` | `string` | no |
| `expiresAt` | `string(date-time)` | no |

## DocumentVerification

| Field | Type | Required |
|---|---|---|
| `valid` | `boolean` | no |
| `verificationCode` | `string` | no |
| `contentHashHex` | `string` | no |
| `algorithm` | `string` | no |
| `signedAt` | `string(date-time)` | no |

## DraftEntryView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `journalId` | `string(uuid)` | no |
| `reference` | `string` | no |
| `entryDate` | `string(date-time)` | no |
| `lines` | `EntryLineView[]` | no |
| `createdAt` | `string(date-time)` | no |
| `postedAt` | `string(date-time)` | no |

## EmergencyContactResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `nom` | `string` | no |
| `prenom` | `string` | no |
| `relation` | `string` | no |
| `telephone` | `string` | no |
| `email` | `string` | no |
| `priorite` | `integer(int32)` | no |

## EmployeeMembershipResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `userId` | `string(uuid)` | no |
| `actorId` | `string(uuid)` | no |
| `email` | `string` | no |
| `firstName` | `string` | no |
| `lastName` | `string` | no |
| `photoUri` | `string` | no |
| `photoId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `agencyName` | `string` | no |
| `roleId` | `string(uuid)` | no |
| `roleName` | `string` | no |
| `status` | `string` | no |
| `jobTitle` | `string` | no |
| `department` | `string` | no |
| `phoneNumber` | `string` | no |
| `employmentType` | `string` | no |
| `matricule` | `string` | no |
| `joinedAt` | `string(date-time)` | no |

## EmployeeProfileResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `actorId` | `string(uuid)` | no |
| `managerId` | `string(uuid)` | no |
| `matricule` | `string` | no |
| `numCnps` | `string` | no |
| `categorie` | `integer(int32)` | no |
| `echelon` | `string` | no |
| `dateEmbauche` | `string(date)` | no |
| `status` | `string` | no |
| `departmentCode` | `string` | no |
| `modePaiement` | `string` | no |
| `compteBancaire` | `string` | no |
| `numMobileMoney` | `string` | no |
| `operateurMm` | `string` | no |
| `actorDisplayName` | `string` | no |
| `actorFirstName` | `string` | no |
| `actorLastName` | `string` | no |
| `actorEmail` | `string` | no |
| `actorPhoneNumber` | `string` | no |
| `actorGender` | `string` | no |
| `actorNationality` | `string` | no |
| `actorBirthDate` | `string(date)` | no |
| `actorPhotoUri` | `string` | no |
| `managerDisplayName` | `string` | no |
| `photoFileId` | `string(uuid)` | no |

## EmployeeResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `actorId` | `string(uuid)` | no |
| `managerId` | `string(uuid)` | no |
| `matricule` | `string` | no |
| `numCnps` | `string` | no |
| `categorie` | `integer(int32)` | no |
| `echelon` | `string` | no |
| `dateEmbauche` | `string(date)` | no |
| `status` | `string` | no |
| `departmentCode` | `string` | no |
| `modePaiement` | `string` | no |
| `compteBancaire` | `string` | no |
| `numMobileMoney` | `string` | no |
| `operateurMm` | `string` | no |
| `actorDisplayName` | `string` | no |
| `photoFileId` | `string(uuid)` | no |

## EmployeeSkillResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `skillId` | `string(uuid)` | no |
| `niveauActuel` | `integer(int32)` | no |
| `niveauAttendu` | `integer(int32)` | no |
| `dateEvaluation` | `string(date)` | no |

## EnableMfaRequest

| Field | Type | Required |
|---|---|---|
| `channel` | `string` | yes |

## EnrollRequest

| Field | Type | Required |
|---|---|---|
| `employeeId` | `string(uuid)` | no |

## EnrollmentResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `trainingId` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `status` | `string` | no |
| `noteEvaluation` | `number` | no |
| `attestationFileId` | `string(uuid)` | no |

## EnsureActorFinancialProfileRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `role` | `string` | yes |
| `referenceCode` | `string` | no |
| `displayName` | `string` | no |

## EntryLine

| Field | Type | Required |
|---|---|---|
| `ordre` | `integer(int32)` | no |
| `lowerBound` | `number` | no |
| `upperBound` | `number` | no |
| `amount` | `number` | no |

## EntryLineRequest

| Field | Type | Required |
|---|---|---|
| `accountId` | `string(uuid)` | yes |
| `debit` | `number` | yes |
| `credit` | `number` | yes |
| `label` | `string` | yes |

## EntryLineView

| Field | Type | Required |
|---|---|---|
| `accountId` | `string(uuid)` | no |
| `debit` | `number` | no |
| `credit` | `number` | no |
| `label` | `string` | no |

## EvaluateObjectiveRequest

| Field | Type | Required |
|---|---|---|
| `noteAtteinte` | `number` | no |
| `commentaire` | `string` | no |

## ExchangeRate

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `sourceCurrencyId` | `string(uuid)` | no |
| `targetCurrencyId` | `string(uuid)` | no |
| `rate` | `number` | no |
| `effectiveDate` | `string(date-time)` | no |

## ExchangeRateView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `sourceCurrency` | `string` | no |
| `targetCurrency` | `string` | no |
| `rate` | `number` | no |
| `rateDate` | `string(date)` | no |
| `createdAt` | `string(date-time)` | no |

## ExecutiveSummaryDto

| Field | Type | Required |
|---|---|---|
| `bilan` | `ExecutiveSummaryItemDto[]` | no |
| `compteResultat` | `ExecutiveSummaryItemDto[]` | no |
| `fluxTresorerie` | `ExecutiveSummaryItemDto[]` | no |

## ExecutiveSummaryItemDto

| Field | Type | Required |
|---|---|---|
| `section` | `string` | no |
| `total` | `number` | no |
| `description` | `string` | no |

## ExpenseLineResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `expenseReportId` | `string(uuid)` | no |
| `description` | `string` | no |
| `montant` | `number` | no |
| `categorie` | `string` | no |
| `justificatifFileId` | `string(uuid)` | no |

## ExpenseReportResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `periode` | `string` | no |
| `totalMontant` | `number` | no |
| `motif` | `string` | no |
| `status` | `string` | no |
| `missionOrderId` | `string(uuid)` | no |

## FinalSettlementResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `periode` | `string` | no |
| `departureDate` | `string(date)` | no |
| `reason` | `string` | no |
| `currency` | `string` | no |
| `seniorityYears` | `integer(int32)` | no |
| `proratedSalary` | `number` | no |
| `leaveCompensation` | `number` | no |
| `noticeIndemnity` | `number` | no |
| `severanceIndemnity` | `number` | no |
| `gratification` | `number` | no |
| `grossSettlement` | `number` | no |
| `loanDeducted` | `number` | no |
| `netSettlement` | `number` | no |
| `status` | `string` | no |

## FiscalYearResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `code` | `string` | no |
| `startDate` | `string(date)` | no |
| `endDate` | `string(date)` | no |
| `status` | `string` | no |
| `organizationId` | `string(uuid)` | no |

## FiscalYearView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `label` | `string` | no |
| `startDate` | `string(date)` | no |
| `endDate` | `string(date)` | no |
| `status` | `string` | no |
| `createdAt` | `string(date-time)` | no |
| `closedAt` | `string(date-time)` | no |

## FixedAsset

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `code` | `string` | no |
| `designation` | `string` | no |
| `acquisitionDate` | `string(date-time)` | no |
| `acquisitionValue` | `number` | no |
| `depreciationType` | `string` | no |
| `lifeSpanYears` | `integer(int32)` | no |
| `assetAccount` | `string` | no |
| `depreciationAccount` | `string` | no |
| `expenseAccount` | `string` | no |
| `status` | `string` | no |

## FixedAssetView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `reference` | `string` | no |
| `label` | `string` | no |
| `acquisitionCost` | `number` | no |
| `usefulLifeMonths` | `integer(int32)` | no |
| `accumulatedDepreciation` | `number` | no |
| `netBookValue` | `number` | no |
| `status` | `string` | no |
| `acquiredAt` | `string(date-time)` | no |
| `lastDepreciatedAt` | `string(date-time)` | no |

## ForgotPasswordRequest

| Field | Type | Required |
|---|---|---|
| `principal` | `string` | yes |

## ForgotPasswordResponse

| Field | Type | Required |
|---|---|---|
| `principal` | `string` | no |
| `matchingAccountCount` | `integer(int64)` | no |
| `selectionToken` | `string` | no |
| `selectionTokenExpiresInSeconds` | `integer(int64)` | no |
| `contexts` | `PasswordResetContextResponse[]` | no |

## FulfillFundRequestRequest

| Field | Type | Required |
|---|---|---|
| `reference` | `string` | yes |

## FundRequestView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `registerId` | `string(uuid)` | no |
| `cashierId` | `string(uuid)` | no |
| `amount` | `number` | no |
| `status` | `string` | no |
| `reason` | `string` | no |
| `createdAt` | `string(date-time)` | no |

## GalleryEventResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `artistProfileId` | `string(uuid)` | no |
| `name` | `string` | no |
| `description` | `string` | no |
| `posterFileId` | `string(uuid)` | no |
| `startAt` | `string(date-time)` | no |
| `endAt` | `string(date-time)` | no |
| `location` | `string` | no |
| `type` | `string` | no |
| `maxCapacity` | `integer(int32)` | no |
| `reservedCount` | `integer(int32)` | no |
| `ticketPrice` | `number` | no |
| `status` | `string` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |

## GalleryReservationResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `eventId` | `string(uuid)` | no |
| `userId` | `string(uuid)` | no |
| `status` | `string` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |

## GalleryTicketResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `reservationId` | `string(uuid)` | no |
| `qrCodeData` | `string` | no |
| `scanned` | `boolean` | no |
| `scannedAt` | `string(date-time)` | no |
| `createdAt` | `string(date-time)` | no |

## GarnishmentResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `type` | `string` | no |
| `beneficiary` | `string` | no |
| `reference` | `string` | no |
| `totalAmount` | `number` | no |
| `remainingBalance` | `number` | no |
| `monthlyAmount` | `number` | no |
| `status` | `string` | no |

## GeneralLedger

| Field | Type | Required |
|---|---|---|
| `startDate` | `string(date)` | no |
| `endDate` | `string(date)` | no |
| `accounts` | `GeneralLedgerAccount[]` | no |

## GeneralLedgerAccount

| Field | Type | Required |
|---|---|---|
| `accountNumber` | `string` | no |
| `accountLabel` | `string` | no |
| `openingBalance` | `number` | no |
| `closingBalance` | `number` | no |
| `totalDebit` | `number` | no |
| `totalCredit` | `number` | no |
| `lines` | `GeneralLedgerLine[]` | no |

## GeneralLedgerLine

| Field | Type | Required |
|---|---|---|
| `date` | `string(date-time)` | no |
| `label` | `string` | no |
| `journal` | `string` | no |
| `debit` | `number` | no |
| `credit` | `number` | no |
| `reference` | `string` | no |

## GeneralizedInventoryCampaignResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `warehouseId` | `string(uuid)` | no |
| `physicalSpaceId` | `string(uuid)` | no |
| `supervisorActorId` | `string(uuid)` | no |
| `campaignCode` | `string` | no |
| `campaignType` | `string` | no |
| `status` | `string` | no |
| `approvalRequired` | `boolean` | no |
| `scopeType` | `string` | no |
| `scheduledAt` | `string(date-time)` | no |
| `startedAt` | `string(date-time)` | no |
| `completedAt` | `string(date-time)` | no |
| `variancePercent` | `number` | no |
| `notes` | `string` | no |

## GeneralizedInventoryView

| Field | Type | Required |
|---|---|---|
| `scopeType` | `string` | no |
| `scopeId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `catalogProductCount` | `integer(int32)` | no |
| `activeCatalogProductCount` | `integer(int32)` | no |
| `scopedProductCount` | `integer(int32)` | no |
| `validatedStockMovementCount` | `integer(int32)` | no |
| `draftStockMovementCount` | `integer(int32)` | no |
| `netQuantity` | `number` | no |
| `inventorySessionCount` | `integer(int32)` | no |
| `validatedInventorySessionCount` | `integer(int32)` | no |
| `draftInventorySessionCount` | `integer(int32)` | no |
| `transformationCount` | `integer(int32)` | no |
| `validatedTransformationCount` | `integer(int32)` | no |
| `draftTransformationCount` | `integer(int32)` | no |
| `warehouseTransferCount` | `integer(int32)` | no |
| `requestedWarehouseTransferCount` | `integer(int32)` | no |
| `completedWarehouseTransferCount` | `integer(int32)` | no |
| `physicalSpaceCount` | `integer(int32)` | no |
| `activePhysicalSpaceCount` | `integer(int32)` | no |
| `resourceCount` | `integer(int64)` | no |
| `assignedResourceCount` | `integer(int64)` | no |
| `reservedResourceCount` | `integer(int64)` | no |
| `openMaintenanceCount` | `integer(int64)` | no |
| `documentCount` | `integer(int32)` | no |
| `stockMovementsByType` | `object` | no |
| `productPositions` | `ProductPositionView[]` | no |

## GeneralizedInventoryViewSnapshot

| Field | Type | Required |
|---|---|---|
| `scopeType` | `string` | no |
| `scopeId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `catalogProductCount` | `integer(int32)` | no |
| `activeCatalogProductCount` | `integer(int32)` | no |
| `scopedProductCount` | `integer(int32)` | no |
| `validatedStockMovementCount` | `integer(int32)` | no |
| `draftStockMovementCount` | `integer(int32)` | no |
| `netQuantity` | `number` | no |
| `inventorySessionCount` | `integer(int32)` | no |
| `validatedInventorySessionCount` | `integer(int32)` | no |
| `draftInventorySessionCount` | `integer(int32)` | no |
| `transformationCount` | `integer(int32)` | no |
| `validatedTransformationCount` | `integer(int32)` | no |
| `draftTransformationCount` | `integer(int32)` | no |
| `warehouseTransferCount` | `integer(int32)` | no |
| `requestedTransferCount` | `integer(int32)` | no |
| `completedTransferCount` | `integer(int32)` | no |
| `physicalSpaceCount` | `integer(int32)` | no |
| `activePhysicalSpaceCount` | `integer(int32)` | no |
| `totalResources` | `integer(int32)` | no |
| `assignedResources` | `integer(int64)` | no |
| `reservedResources` | `integer(int64)` | no |
| `openMaintenanceCount` | `integer(int64)` | no |
| `documentCount` | `integer(int32)` | no |
| `movementCountsByType` | `object` | no |
| `productPositions` | `ProductPositionSnapshot[]` | no |

## GenerateAccountRequest

| Field | Type | Required |
|---|---|---|
| `name` | `string` | yes |
| `accountType` | `string` | yes |
| `externalId` | `string(uuid)` | no |
| `notes` | `string` | no |

## GenerateEntryFromTemplateRequest

| Field | Type | Required |
|---|---|---|
| `amountTtc` | `number` | yes |
| `vatRate` | `number` | yes |
| `dynamicAccountId` | `string(uuid)` | no |
| `thirdPartyId` | `string(uuid)` | no |
| `fiscalYearId` | `string(uuid)` | yes |
| `ledgerId` | `string(uuid)` | no |

## GenerateReportExportRequest

| Field | Type | Required |
|---|---|---|
| `reportType` | `string` | yes |
| `format` | `string` | yes |

## GenerateRequest

| Field | Type | Required |
|---|---|---|
| `fichierId` | `string(uuid)` | no |

## GeneratedWalletResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `label` | `string` | no |
| `publicKey` | `string` | no |
| `privateKey` | `string` | no |
| `fingerprint` | `string` | no |
| `createdAt` | `string(date-time)` | no |

## GovernanceActionRequest

| Field | Type | Required |
|---|---|---|
| `reason` | `string` | no |

## GrandLivreDto

| Field | Type | Required |
|---|---|---|
| `noCompte` | `string` | no |
| `libelleCompte` | `string` | no |
| `soldeOuverture` | `number` | no |
| `totalDebit` | `number` | no |
| `totalCredit` | `number` | no |
| `soldeCloture` | `number` | no |
| `lignes` | `LigneGrandLivreDto[]` | no |

## IdentifyAccountRequest

| Field | Type | Required |
|---|---|---|
| `principal` | `string` | yes |

## IdentifyAccountResponse

| Field | Type | Required |
|---|---|---|
| `principal` | `string` | no |
| `accountExists` | `boolean` | no |
| `nextStep` | `string` | no |
| `matchingAccountCount` | `integer(int64)` | no |

## ImportStatementLinesRequest

| Field | Type | Required |
|---|---|---|
| `lines` | `LineEntry[]` | yes |

## IncomeStatement

| Field | Type | Required |
|---|---|---|
| `startDate` | `string(date)` | no |
| `endDate` | `string(date)` | no |
| `revenues` | `ReportLineItem[]` | no |
| `expenses` | `ReportLineItem[]` | no |
| `totalRevenues` | `number` | no |
| `totalExpenses` | `number` | no |
| `netResult` | `number` | no |

## IntegrityReport

| Field | Type | Required |
|---|---|---|
| `scanned` | `integer(int64)` | no |
| `tampered` | `integer(int64)` | no |
| `missingHash` | `integer(int64)` | no |
| `tamperedIds` | `string(uuid)[]` | no |
| `scannedFrom` | `string(date-time)` | no |
| `scannedTo` | `string(date-time)` | no |
| `integrityEnabled` | `boolean` | no |

## InterviewResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `applicationId` | `string(uuid)` | no |
| `type` | `string` | no |
| `dateHeure` | `string(date-time)` | no |
| `lieu` | `string` | no |
| `interviewerPartyId` | `string(uuid)` | no |
| `interviewerDisplayName` | `string` | no |
| `notes` | `string` | no |
| `resultat` | `string` | no |

## InventorySessionResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `productId` | `string(uuid)` | no |
| `referenceNumber` | `string` | no |
| `countedQuantity` | `number` | no |
| `status` | `string` | no |

## InviteEmployeeRequest

| Field | Type | Required |
|---|---|---|
| `firstName` | `string` | no |
| `lastName` | `string` | no |
| `email` | `string(email)` | yes |
| `password` | `string` | no |
| `roleId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `permissions` | `string[]` | no |
| `jobTitle` | `string` | no |
| `department` | `string` | no |
| `phoneNumber` | `string` | no |
| `employmentType` | `string` | no |
| `photoUri` | `string` | no |
| `photoId` | `string(uuid)` | no |

## InvoiceAccountingView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `invoiceId` | `string(uuid)` | no |
| `customerThirdPartyId` | `string(uuid)` | no |
| `customerAccountingAccount` | `string` | no |
| `accountingStatus` | `string` | no |
| `createdAt` | `string(date-time)` | no |

## InvoiceArtworkSaleRequest

| Field | Type | Required |
|---|---|---|
| `customerThirdPartyId` | `string(uuid)` | yes |
| `productId` | `string(uuid)` | yes |
| `salesOrderId` | `string(uuid)` | yes |
| `quantity` | `number` | yes |
| `unitPrice` | `number` | yes |
| `currency` | `string` | yes |
| `invoiceNumber` | `string` | yes |

## InvoiceLineResponse

| Field | Type | Required |
|---|---|---|
| `productId` | `string(uuid)` | no |
| `quantity` | `number` | no |
| `unitPrice` | `number` | no |
| `lineAmount` | `number` | no |

## InvoiceResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `customerThirdPartyId` | `string(uuid)` | no |
| `orderId` | `string(uuid)` | no |
| `productId` | `string(uuid)` | no |
| `invoiceNumber` | `string` | no |
| `quantity` | `number` | no |
| `unitPrice` | `number` | no |
| `totalQuantity` | `number` | no |
| `subtotalAmount` | `number` | no |
| `totalAmount` | `number` | no |
| `settledAmount` | `number` | no |
| `outstandingAmount` | `number` | no |
| `currency` | `string` | no |
| `status` | `string` | no |
| `paymentStatus` | `string` | no |
| `lines` | `InvoiceLineResponse[]` | no |

## InvoiceSettlementResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `bankAccountId` | `string(uuid)` | no |
| `invoiceId` | `string(uuid)` | no |
| `settlementNumber` | `string` | no |
| `paymentMethod` | `string` | no |
| `amount` | `number` | no |
| `currency` | `string` | no |
| `status` | `string` | no |

## InvoiceSummaryView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `customerThirdPartyId` | `string(uuid)` | no |
| `orderId` | `string(uuid)` | no |
| `invoiceNumber` | `string` | no |
| `totalAmount` | `number` | no |
| `settledAmount` | `number` | no |
| `outstandingAmount` | `number` | no |
| `currency` | `string` | no |
| `status` | `string` | no |
| `paymentStatus` | `string` | no |

## InvoiceUploadView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `filename` | `string` | no |
| `contentType` | `string` | no |
| `sizeBytes` | `integer(int64)` | no |
| `createdAt` | `string(date-time)` | no |

## IssueOtpRequest

| Field | Type | Required |
|---|---|---|
| `channel` | `string` | yes |
| `recipient` | `string` | yes |
| `purpose` | `string` | no |

## IssuePasswordResetRequest

| Field | Type | Required |
|---|---|---|
| `selectionToken` | `string` | yes |
| `contextId` | `string` | yes |

## IssuedAuthChallengeResponse

| Field | Type | Required |
|---|---|---|
| `deliveryMode` | `string` | no |
| `challengeTokenPreview` | `string` | no |
| `expiresInSeconds` | `integer(int64)` | no |

## JobOfferResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `poste` | `string` | no |
| `departement` | `string` | no |
| `localisation` | `string` | no |
| `competencesRequises` | `string` | no |
| `dateLimite` | `string(date)` | no |
| `packageSalarial` | `string` | no |
| `status` | `string` | no |

## JournalAuditView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `action` | `string` | no |
| `targetType` | `string` | no |
| `targetId` | `string(uuid)` | no |
| `details` | `string` | no |
| `createdAt` | `string(date-time)` | no |

## JournalEntryResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `entryNumber` | `string` | no |
| `label` | `string` | no |
| `accountingDate` | `string(date)` | no |
| `status` | `string` | no |
| `totalDebit` | `number` | no |
| `totalCredit` | `number` | no |

## JournalSummaryView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `code` | `string` | no |
| `label` | `string` | no |
| `type` | `string` | no |
| `active` | `boolean` | no |

## JournalView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `code` | `string` | no |
| `label` | `string` | no |
| `type` | `string` | no |
| `active` | `boolean` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |

## JsonNode

Type: `object`

## JustifyReconciliationRequest

| Field | Type | Required |
|---|---|---|
| `justification` | `string` | yes |

## KernelAgencyView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `code` | `string` | no |
| `shortName` | `string` | no |
| `longName` | `string` | no |
| `status` | `string` | no |
| `active` | `boolean` | no |

## KernelOrganizationView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `code` | `string` | no |
| `shortName` | `string` | no |
| `longName` | `string` | no |
| `status` | `string` | no |
| `active` | `boolean` | no |
| `services` | `string[]` | no |

## LeaveBalanceResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `type` | `string` | no |
| `acquis` | `number` | no |
| `pris` | `number` | no |
| `soldeRestant` | `number` | no |
| `annee` | `integer(int32)` | no |

## LeaveResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `type` | `string` | no |
| `dateDebut` | `string(date)` | no |
| `dateFin` | `string(date)` | no |
| `nbJours` | `number` | no |
| `status` | `string` | no |
| `motif` | `string` | no |
| `valideurPartyId` | `string(uuid)` | no |
| `valideurDisplayName` | `string` | no |
| `dateValidation` | `string(date-time)` | no |
| `commentaireValideur` | `string` | no |
| `justificatifFileId` | `string(uuid)` | no |

## LedgerResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `code` | `string` | no |
| `label` | `string` | no |
| `type` | `string` | no |
| `organizationId` | `string(uuid)` | no |

## LetteringView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `debitEntryId` | `string(uuid)` | no |
| `creditEntryId` | `string(uuid)` | no |
| `matchedAmount` | `number` | no |
| `createdAt` | `string(date-time)` | no |

## LigneBalanceDto

| Field | Type | Required |
|---|---|---|
| `noCompte` | `string` | no |
| `libelle` | `string` | no |
| `soldeOuvertureDebit` | `number` | no |
| `soldeOuvertureCredit` | `number` | no |
| `mouvementDebit` | `number` | no |
| `mouvementCredit` | `number` | no |
| `soldeClotureDebit` | `number` | no |
| `soldeClotureCredit` | `number` | no |

## LigneGrandLivreDto

| Field | Type | Required |
|---|---|---|
| `ecritureId` | `string(uuid)` | no |
| `date` | `string(date-time)` | no |
| `journal` | `string` | no |
| `reference` | `string` | no |
| `libelle` | `string` | no |
| `debit` | `number` | no |
| `credit` | `number` | no |

## LineEntry

| Field | Type | Required |
|---|---|---|
| `operationDate` | `string(date)` | yes |
| `valueDate` | `string(date)` | no |
| `label` | `string` | yes |
| `amount` | `number` | yes |
| `direction` | `string` | yes |
| `referenceCode` | `string` | no |

## LineItemResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `poLineId` | `string(uuid)` | no |
| `productId` | `string(uuid)` | no |
| `productName` | `string` | no |
| `sku` | `string` | no |
| `orderedQty` | `integer(int32)` | no |
| `receivedQty` | `integer(int32)` | no |
| `note` | `string` | no |

## LineResponse

| Field | Type | Required |
|---|---|---|
| `employeeId` | `string(uuid)` | no |
| `matricule` | `string` | no |
| `employeeName` | `string` | no |
| `socialSecurityNo` | `string` | no |
| `grossBase` | `number` | no |
| `employeeContribution` | `number` | no |
| `employerContribution` | `number` | no |

## LinkAgencyDomainRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `domainId` | `string(uuid)` | yes |

## LinkOrganizationActorRequest

| Field | Type | Required |
|---|---|---|
| `actorId` | `string(uuid)` | yes |
| `type` | `string` | no |

## LinkOrganizationDomainRequest

| Field | Type | Required |
|---|---|---|
| `domainId` | `string(uuid)` | yes |

## LinkPointOfInterestRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `agencyId` | `string(uuid)` | yes |
| `poiId` | `string(uuid)` | yes |
| `distanceMeters` | `integer(int32)` | no |
| `description` | `string` | no |

## LoanAdvanceResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `montant` | `number` | no |
| `soldeRestant` | `number` | no |
| `mensualite` | `number` | no |
| `status` | `string` | no |
| `dateDebut` | `string(date)` | no |
| `nbEcheances` | `integer(int32)` | no |
| `motif` | `string` | no |
| `approvedBy` | `string(uuid)` | no |

## LoanRepaymentResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `loanId` | `string(uuid)` | no |
| `runId` | `string(uuid)` | no |
| `period` | `string` | no |
| `montant` | `number` | no |
| `soldeApres` | `number` | no |
| `recordedAt` | `string(date-time)` | no |

## LoginRequest

| Field | Type | Required |
|---|---|---|
| `principal` | `string` | yes |
| `password` | `string` | yes |

## LoginResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `actorId` | `string(uuid)` | no |
| `username` | `string` | no |
| `email` | `string` | no |
| `phoneNumber` | `string` | no |
| `authProvider` | `string` | no |
| `externalSubject` | `string` | no |
| `status` | `string` | no |
| `plan` | `string` | no |
| `onboardingStatus` | `string` | no |
| `onboardingStep` | `integer(int32)` | no |
| `accountType` | `string` | no |
| `businessType` | `string` | no |
| `onboardingPayload` | `string` | no |
| `emailVerified` | `boolean` | no |
| `emailVerifiedAt` | `string(date-time)` | no |
| `phoneVerified` | `boolean` | no |
| `phoneVerifiedAt` | `string(date-time)` | no |
| `mfaEnabled` | `boolean` | no |
| `mfaChannel` | `string` | no |
| `accessToken` | `string` | no |
| `sessionToken` | `string` | no |
| `tokenType` | `string` | no |
| `expiresInSeconds` | `integer(int64)` | no |
| `sharedSession` | `SharedSsoSessionResponse` | no |
| `authorities` | `string[]` | no |
| `organizations` | `UserOrganizationAccessResponse[]` | no |

## LookupTableResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `code` | `string` | no |
| `label` | `string` | no |
| `countryCode` | `string` | no |
| `effectiveFrom` | `string(date)` | no |
| `effectiveTo` | `string(date)` | no |
| `active` | `boolean` | no |
| `entries` | `EntryLine[]` | no |

## MaintenanceRecordResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `resourceId` | `string(uuid)` | no |
| `maintenanceType` | `string` | no |
| `description` | `string` | no |
| `status` | `string` | no |
| `completedAt` | `string(date-time)` | no |

## ManualMatchRequest

| Field | Type | Required |
|---|---|---|
| `statementLineId` | `string(uuid)` | yes |
| `matchedEntityType` | `string` | yes |
| `matchedEntityId` | `string(uuid)` | yes |

## ManualReconcileBankTransactionRequest

| Field | Type | Required |
|---|---|---|
| `transactionId` | `string(uuid)` | yes |
| `statementId` | `string(uuid)` | no |
| `statementLineId` | `string(uuid)` | no |

## MatchWithNewTransactionRequest

| Field | Type | Required |
|---|---|---|
| `statementLineId` | `string(uuid)` | yes |
| `transactionTypeId` | `string(uuid)` | yes |
| `label` | `string` | no |
| `partnerName` | `string` | no |
| `notes` | `string` | no |

## MaterialActionRequest

| Field | Type | Required |
|---|---|---|
| `items` | `MaterialQuantityUpdate[]` | yes |
| `note` | `string` | no |

## MaterialQuantityUpdate

| Field | Type | Required |
|---|---|---|
| `productId` | `string(uuid)` | yes |
| `quantity` | `number` | yes |
| `note` | `string` | no |

## MaterialRequestItemInput

| Field | Type | Required |
|---|---|---|
| `productId` | `string(uuid)` | yes |
| `quantity` | `number` | yes |
| `note` | `string` | no |

## MaterialRequestItemResponse

| Field | Type | Required |
|---|---|---|
| `productId` | `string(uuid)` | no |
| `quantityRequested` | `number` | no |
| `quantityIssued` | `number` | no |
| `quantityReturned` | `number` | no |
| `note` | `string` | no |

## MaterialRequestResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `departmentId` | `string(uuid)` | no |
| `status` | `string` | no |
| `reasonCode` | `string` | no |
| `reasonText` | `string` | no |
| `requestedBy` | `string` | no |
| `approvedBy` | `string` | no |
| `approvedAt` | `string(date-time)` | no |
| `issuedBy` | `string` | no |
| `issuedAt` | `string(date-time)` | no |
| `expectedReturnAt` | `string(date-time)` | no |
| `closedBy` | `string` | no |
| `closedAt` | `string(date-time)` | no |
| `closeReason` | `string` | no |
| `updatedAt` | `string(date-time)` | no |
| `items` | `MaterialRequestItemResponse[]` | no |

## MaterialResourceResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `resourceCode` | `string` | no |
| `name` | `string` | no |
| `category` | `string` | no |
| `serialNumber` | `string` | no |
| `status` | `string` | no |
| `latitude` | `number(double)` | no |
| `longitude` | `number(double)` | no |
| `ipAddress` | `string` | no |
| `macAddress` | `string` | no |

## MaterialResourceSearchResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `resourceCode` | `string` | no |
| `name` | `string` | no |
| `category` | `string` | no |
| `serialNumber` | `string` | no |
| `status` | `string` | no |
| `latitude` | `number(double)` | no |
| `longitude` | `number(double)` | no |
| `ipAddress` | `string` | no |
| `macAddress` | `string` | no |

## MediaAssetResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `targetType` | `string` | no |
| `targetId` | `string(uuid)` | no |
| `fileId` | `string(uuid)` | no |
| `mimeType` | `string` | no |
| `position` | `integer(int32)` | no |
| `altText` | `string` | no |

## MedicalCertificateResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `typeCertificat` | `string` | no |
| `dateEmission` | `string(date)` | no |
| `dateExpiration` | `string(date)` | no |
| `statut` | `string` | no |
| `fichierId` | `string(uuid)` | no |

## MedicalVisitResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `dateVisite` | `string(date)` | no |
| `medecin` | `string` | no |
| `resultatAptitude` | `string` | no |
| `restrictions` | `string` | no |
| `prochaineEcheance` | `string(date)` | no |
| `certificatFileId` | `string(uuid)` | no |

## MineBlockRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `chainCode` | `string` | no |
| `minedBy` | `string` | no |
| `difficulty` | `integer(int32)` | no |

## MissionOrderResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `destination` | `string` | no |
| `objet` | `string` | no |
| `dateDebut` | `string(date)` | no |
| `dateFin` | `string(date)` | no |
| `montantAvance` | `number` | no |
| `centreCout` | `string` | no |
| `status` | `string` | no |
| `parentOrderId` | `string(uuid)` | no |
| `decisionReason` | `string` | no |
| `decidedAt` | `string(date-time)` | no |

## MontantRequest

| Field | Type | Required |
|---|---|---|
| `montant` | `number` | no |

## MyClientApplicationResponse

| Field | Type | Required |
|---|---|---|
| `clientApplicationId` | `string(uuid)` | no |
| `clientId` | `string` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `allowedServices` | `string[]` | no |
| `services` | `ServiceEntitlement[]` | no |

## MyLoanRequest

| Field | Type | Required |
|---|---|---|
| `montant` | `number` | no |
| `nbEcheances` | `integer(int32)` | no |
| `motif` | `string` | no |

## MyPayslipResponse

| Field | Type | Required |
|---|---|---|
| `entryId` | `string(uuid)` | no |
| `runId` | `string(uuid)` | no |
| `periode` | `string` | no |
| `runStatus` | `string` | no |
| `salaireBase` | `number` | no |
| `brut` | `number` | no |
| `net` | `number` | no |
| `totalDeductions` | `number` | no |
| `incomeTax` | `number` | no |
| `employerCharges` | `number` | no |
| `paymentStatus` | `string` | no |
| `paymentChannel` | `string` | no |
| `paymentDate` | `string(date-time)` | no |

## NestedAddressRequest

| Field | Type | Required |
|---|---|---|
| `type` | `string` | yes |
| `addressLine1` | `string` | yes |
| `addressLine2` | `string` | no |
| `city` | `string` | no |
| `state` | `string` | no |
| `locality` | `string` | no |
| `countryId` | `string(uuid)` | no |
| `zipCode` | `string` | no |
| `postalCode` | `string` | no |
| `poBox` | `string` | no |
| `isDefault` | `boolean` | no |
| `neighborhood` | `string` | no |
| `informalDescription` | `string` | no |
| `latitude` | `number(double)` | no |
| `longitude` | `number(double)` | no |

## NestedContactRequest

| Field | Type | Required |
|---|---|---|
| `firstName` | `string` | no |
| `lastName` | `string` | no |
| `title` | `string` | no |
| `isEmailVerified` | `boolean` | no |
| `isPhoneNumberVerified` | `boolean` | no |
| `isFavorite` | `boolean` | no |
| `phoneNumber` | `string` | no |
| `secondaryPhoneNumber` | `string` | no |
| `faxNumber` | `string` | no |
| `email` | `string` | no |
| `secondaryEmail` | `string` | no |

## NotificationDelivery

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `recipientUserId` | `string(uuid)` | no |
| `recipientAddress` | `string` | no |
| `channel` | `string` | no |
| `templateCode` | `string` | no |
| `subject` | `string` | no |
| `body` | `string` | no |
| `variables` | `object` | no |
| `metadata` | `object` | no |
| `status` | `string` | no |
| `providerType` | `string` | no |
| `providerMessageId` | `string` | no |
| `errorMessage` | `string` | no |
| `requestedAt` | `string(date-time)` | no |
| `sentAt` | `string(date-time)` | no |

## NotificationPreference

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `userId` | `string(uuid)` | no |
| `channel` | `string` | no |
| `enabled` | `boolean` | no |
| `locale` | `string` | no |
| `updatedAt` | `string(date-time)` | no |

## NotificationProvider

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `channel` | `string` | no |
| `type` | `string` | no |
| `name` | `string` | no |
| `defaultProvider` | `boolean` | no |
| `active` | `boolean` | no |
| `configurationJson` | `string` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |

## NotificationReminder

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `templateCode` | `string` | no |
| `channel` | `string` | no |
| `recipientUserId` | `string(uuid)` | no |
| `recipientAddress` | `string` | no |
| `dueAt` | `string(date-time)` | no |
| `active` | `boolean` | no |
| `variables` | `object` | no |
| `metadata` | `object` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |

## NotificationTemplate

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `code` | `string` | no |
| `channel` | `string` | no |
| `locale` | `string` | no |
| `subjectTemplate` | `string` | no |
| `bodyTemplate` | `string` | no |
| `active` | `boolean` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |

## NotificationView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `category` | `string` | no |
| `message` | `string` | no |
| `status` | `string` | no |
| `createdAt` | `string(date-time)` | no |
| `acknowledgedAt` | `string(date-time)` | no |

## NotifyUnauthorizedRequest

| Field | Type | Required |
|---|---|---|
| `message` | `string` | yes |
| `endpoint` | `string` | no |

## ObjectiveResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `reviewId` | `string(uuid)` | no |
| `description` | `string` | no |
| `poids` | `number` | no |
| `noteAtteinte` | `number` | no |
| `commentaire` | `string` | no |

## OnboardingFlow

| Field | Type | Required |
|---|---|---|
| `steps` | `OnboardingStep[]` | no |

## OnboardingStep

| Field | Type | Required |
|---|---|---|
| `order` | `integer(int32)` | no |
| `description` | `string` | no |
| `endpoint` | `string` | no |
| `method` | `string` | no |

## OnboardingTaskResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `titre` | `string` | no |
| `description` | `string` | no |
| `assignedToPartyId` | `string(uuid)` | no |
| `echeance` | `string(date)` | no |
| `status` | `string` | no |

## OpenAccountingItemResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `counterpartyThirdPartyId` | `string(uuid)` | no |
| `reference` | `string` | no |
| `balanceDue` | `number` | no |
| `currency` | `string` | no |
| `status` | `string` | no |
| `paymentStatus` | `string` | no |
| `direction` | `string` | no |

## OpenItemSummaryView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `counterpartyThirdPartyId` | `string(uuid)` | no |
| `reference` | `string` | no |
| `balanceDue` | `number` | no |
| `currency` | `string` | no |
| `status` | `string` | no |
| `paymentStatus` | `string` | no |
| `direction` | `string` | no |

## OpenReconciliationRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `bankAccountId` | `string(uuid)` | yes |
| `statementId` | `string(uuid)` | yes |
| `referenceNumber` | `string` | no |

## OpeningHoursExceptionRequest

| Field | Type | Required |
|---|---|---|
| `exceptionDate` | `string(date)` | yes |
| `label` | `string` | no |
| `opensAt` | `string` | no |
| `closesAt` | `string` | no |
| `closed` | `boolean` | no |

## OpeningHoursExceptionResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `exceptionDate` | `string(date)` | no |
| `label` | `string` | no |
| `opensAt` | `string` | no |
| `closesAt` | `string` | no |
| `closed` | `boolean` | no |

## OpeningHoursExceptionView

| Field | Type | Required |
|---|---|---|
| `exceptionDate` | `string(date)` | no |
| `label` | `string` | no |
| `opensAt` | `string` | no |
| `closesAt` | `string` | no |
| `closed` | `boolean` | no |

## OpeningHoursResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `dayOfWeek` | `string` | no |
| `opensAt` | `string` | no |
| `closesAt` | `string` | no |
| `closed` | `boolean` | no |

## OpeningHoursView

| Field | Type | Required |
|---|---|---|
| `dayOfWeek` | `string` | no |
| `opensAt` | `string` | no |
| `closesAt` | `string` | no |
| `closed` | `boolean` | no |

## OperationTemplateResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `code` | `string` | no |
| `label` | `string` | no |
| `operationType` | `string` | no |

## OperationalComplianceOverview

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `totalAssets` | `integer(int32)` | no |
| `nonCompliantAssets` | `integer(int64)` | no |
| `retiredAssets` | `integer(int64)` | no |
| `pendingDocuments` | `integer(int64)` | no |
| `expiredDocuments` | `integer(int64)` | no |
| `pendingInventoryCampaigns` | `integer(int64)` | no |
| `unreadySites` | `integer(int64)` | no |

## OperationalPolicyResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `assignmentRequiresApproval` | `boolean` | no |
| `allowCrossAgencyAssetAssignment` | `boolean` | no |
| `siteOpeningChecklistRequired` | `boolean` | no |
| `mandatoryDocumentApproval` | `boolean` | no |
| `inventoryVarianceTolerancePercent` | `integer(int32)` | no |
| `maintenanceAlertThresholdDays` | `integer(int32)` | no |
| `lowUtilizationThresholdPercent` | `integer(int32)` | no |
| `maxOpenInventoryCampaigns` | `integer(int32)` | no |
| `requireInventorySupervisorApproval` | `boolean` | no |
| `automaticLifecycleEvents` | `boolean` | no |
| `strictDocumentExpiry` | `boolean` | no |

## OperationalResponsibilityResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `physicalSpaceId` | `string(uuid)` | no |
| `actorId` | `string(uuid)` | no |
| `responsibilityType` | `string` | no |
| `primaryResponsibility` | `boolean` | no |
| `active` | `boolean` | no |
| `notes` | `string` | no |

## OperationalSiteProfileResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `siteCategory` | `string` | no |
| `operatingModel` | `string` | no |
| `openingStatus` | `string` | no |
| `cashEnabled` | `boolean` | no |
| `warehouseEnabled` | `boolean` | no |
| `maintenanceEnabled` | `boolean` | no |
| `inventoryEnabled` | `boolean` | no |
| `documentComplianceRequired` | `boolean` | no |
| `defaultPhysicalSpaceId` | `string(uuid)` | no |
| `readinessNotes` | `string` | no |
| `commissionedAt` | `string(date-time)` | no |

## OperationalSiteProfileView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `siteCategory` | `string` | no |
| `operatingModel` | `string` | no |
| `openingStatus` | `string` | no |
| `cashEnabled` | `boolean` | no |
| `warehouseEnabled` | `boolean` | no |
| `maintenanceEnabled` | `boolean` | no |
| `inventoryEnabled` | `boolean` | no |
| `documentComplianceRequired` | `boolean` | no |
| `defaultPhysicalSpaceId` | `string(uuid)` | no |
| `readinessNotes` | `string` | no |
| `commissionedAt` | `string(date-time)` | no |

## OperationalSiteReadinessSnapshot

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `openingStatus` | `string` | no |
| `totalPhysicalSpaces` | `integer(int32)` | no |
| `activePhysicalSpaces` | `integer(int64)` | no |
| `totalResponsibilities` | `integer(int32)` | no |
| `primaryResponsibilities` | `integer(int64)` | no |
| `ready` | `boolean` | no |
| `readinessStatus` | `string` | no |

## OperationalSiteReadinessView

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `openingStatus` | `string` | no |
| `totalPhysicalSpaces` | `integer(int32)` | no |
| `activePhysicalSpaces` | `integer(int64)` | no |
| `totalResponsibilities` | `integer(int32)` | no |
| `primaryResponsibilities` | `integer(int64)` | no |
| `ready` | `boolean` | no |
| `readinessStatus` | `string` | no |

## OperationalSiteSnapshotView

| Field | Type | Required |
|---|---|---|
| `agencyId` | `string(uuid)` | no |
| `openingStatus` | `string` | no |
| `ready` | `boolean` | no |
| `readinessStatus` | `string` | no |

## OperationalSiteView

| Field | Type | Required |
|---|---|---|
| `scopeType` | `string` | no |
| `scopeId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `agencyType` | `string` | no |
| `code` | `string` | no |
| `name` | `string` | no |
| `city` | `string` | no |
| `country` | `string` | no |
| `location` | `string` | no |
| `active` | `boolean` | no |
| `warehouse` | `boolean` | no |
| `openingHours` | `OpeningHoursView[]` | no |
| `upcomingExceptions` | `OpeningHoursExceptionView[]` | no |
| `pointsOfInterest` | `PointOfInterestView[]` | no |
| `physicalLayout` | `PhysicalLayoutView` | no |
| `assetPortfolio` | `AssetPortfolioSnapshot` | no |
| `documents` | `SiteDocumentView` | no |
| `inventory` | `GeneralizedInventoryView` | no |
| `capabilities` | `string[]` | no |

## OperationalSiteViewSnapshot

| Field | Type | Required |
|---|---|---|
| `scopeType` | `string` | no |
| `scopeId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `agencyType` | `string` | no |
| `code` | `string` | no |
| `name` | `string` | no |
| `city` | `string` | no |
| `country` | `string` | no |
| `location` | `string` | no |
| `active` | `boolean` | no |
| `warehouse` | `boolean` | no |
| `physicalLayout` | `PhysicalLayoutSnapshot` | no |
| `assetPortfolio` | `AssetPortfolioSnapshot` | no |
| `documents` | `SiteDocumentSnapshot` | no |
| `inventory` | `GeneralizedInventoryViewSnapshot` | no |
| `capabilities` | `string[]` | no |

## OrganizationActorResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `actorId` | `string(uuid)` | no |
| `type` | `string` | no |

## OrganizationCommercialSubscriptionResponse

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `planCode` | `string` | no |
| `addOnCodes` | `string[]` | no |
| `serviceCodes` | `string[]` | no |
| `serviceQuotas` | `CommercialServiceQuotaResponse[]` | no |
| `dependencyIssues` | `OrganizationServiceDependencyIssueResponse[]` | no |
| `entitlements` | `OrganizationServicesResponse` | no |

## OrganizationDomainResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `domainId` | `string(uuid)` | no |

## OrganizationOperationalPilotageView

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `operationalPolicyId` | `string(uuid)` | no |
| `assetPortfolio` | `AssetPortfolioSnapshot` | no |
| `advancedAssetOverview` | `AdvancedAssetOverviewSnapshot` | no |
| `generalizedInventory` | `GeneralizedInventoryViewSnapshot` | no |
| `documentHubOverview` | `DocumentHubOverviewSnapshot` | no |
| `documentGovernanceOverview` | `DocumentGovernanceOverviewSnapshot` | no |
| `campaignSummary` | `CampaignSummaryView` | no |
| `siteSnapshots` | `OperationalSiteSnapshotView[]` | no |

## OrganizationResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `businessActorId` | `string(uuid)` | no |
| `governanceStatus` | `string` | no |
| `governedByUserId` | `string(uuid)` | no |
| `governedAt` | `string(date-time)` | no |
| `governanceReason` | `string` | no |
| `code` | `string` | no |
| `service` | `string` | no |
| `isIndividualBusiness` | `boolean` | no |
| `email` | `string` | no |
| `shortName` | `string` | no |
| `longName` | `string` | no |
| `description` | `string` | no |
| `logoUri` | `string` | no |
| `logoId` | `string(uuid)` | no |
| `websiteUrl` | `string` | no |
| `socialNetwork` | `string` | no |
| `businessRegistrationNumber` | `string` | no |
| `taxNumber` | `string` | no |
| `capitalShare` | `number` | no |
| `ceoName` | `string` | no |
| `yearFounded` | `integer(int32)` | no |
| `keywords` | `string[]` | no |
| `numberOfEmployees` | `integer(int32)` | no |
| `legalForm` | `string` | no |
| `isActive` | `boolean` | no |
| `status` | `string` | no |
| `deletedAt` | `string(date-time)` | no |
| `legalName` | `string` | no |
| `displayName` | `string` | no |
| `organizationType` | `string` | no |

## OrganizationRoleResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `code` | `string` | no |
| `name` | `string` | no |
| `permissions` | `string[]` | no |

## OrganizationScopedResourceRequest

| Field | Type | Required |
|---|---|---|
| `agencyId` | `string(uuid)` | yes |
| `resourceCode` | `string` | yes |
| `name` | `string` | yes |
| `category` | `string` | yes |
| `serialNumber` | `string` | yes |
| `latitude` | `number(double)` | no |
| `longitude` | `number(double)` | no |
| `ipAddress` | `string` | no |
| `macAddress` | `string` | no |

## OrganizationSearchResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `businessActorId` | `string(uuid)` | no |
| `code` | `string` | no |
| `service` | `string` | no |
| `shortName` | `string` | no |
| `longName` | `string` | no |
| `legalForm` | `string` | no |
| `isActive` | `boolean` | no |
| `status` | `string` | no |
| `legalName` | `string` | no |
| `displayName` | `string` | no |
| `organizationType` | `string` | no |

## OrganizationServiceCatalogResponse

| Field | Type | Required |
|---|---|---|
| `code` | `string` | no |
| `name` | `string` | no |
| `description` | `string` | no |
| `mandatory` | `boolean` | no |
| `subscribable` | `boolean` | no |
| `requiredDependencies` | `string[]` | no |
| `recommendedDependencies` | `string[]` | no |
| `packs` | `string[]` | no |

## OrganizationServiceDependencyIssueResponse

| Field | Type | Required |
|---|---|---|
| `serviceCode` | `string` | no |
| `missingRequiredServices` | `string[]` | no |
| `missingRecommendedServices` | `string[]` | no |

## OrganizationServicePackResponse

| Field | Type | Required |
|---|---|---|
| `code` | `string` | no |
| `name` | `string` | no |
| `description` | `string` | no |
| `serviceCodes` | `string[]` | no |

## OrganizationServiceQuotaResponse

| Field | Type | Required |
|---|---|---|
| `serviceCode` | `string` | no |
| `requestQuotaLimit` | `integer(int64)` | no |
| `requestQuotaWindowSeconds` | `integer(int64)` | no |

## OrganizationServicesResponse

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `subscribedServices` | `string[]` | no |
| `effectiveServices` | `string[]` | no |
| `serviceQuotas` | `OrganizationServiceQuotaResponse[]` | no |
| `dependencyIssues` | `OrganizationServiceDependencyIssueResponse[]` | no |

## OrganizationSummaryView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `code` | `string` | no |
| `shortName` | `string` | no |
| `longName` | `string` | no |
| `status` | `string` | no |
| `isActive` | `boolean` | no |

## OtpChallengeResponse

| Field | Type | Required |
|---|---|---|
| `deliveryMode` | `string` | no |
| `challengeToken` | `string` | no |
| `codePreview` | `string` | no |
| `expiresInSeconds` | `integer(int64)` | no |

## OtpVerificationResponse

| Field | Type | Required |
|---|---|---|
| `verified` | `boolean` | no |
| `channel` | `string` | no |
| `recipient` | `string` | no |
| `purpose` | `string` | no |

## P2PTransferRequest

| Field | Type | Required |
|---|---|---|
| `fromCustomerId` | `string(uuid)` | yes |
| `toCustomerId` | `string(uuid)` | yes |
| `amount` | `number` | yes |
| `reference` | `string` | yes |

## PartyRef

| Field | Type | Required |
|---|---|---|
| `partyType` | `string` | no |
| `partyId` | `string(uuid)` | no |

## PasswordResetContextResponse

| Field | Type | Required |
|---|---|---|
| `contextId` | `string` | no |
| `tenantId` | `string(uuid)` | no |
| `userId` | `string(uuid)` | no |
| `actorId` | `string(uuid)` | no |
| `username` | `string` | no |
| `email` | `string` | no |

## PayBillRequest

| Field | Type | Required |
|---|---|---|
| `amount` | `number` | yes |
| `sessionId` | `string(uuid)` | no |
| `registerId` | `string(uuid)` | no |

## PayElementResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `code` | `string` | no |
| `label` | `string` | no |
| `category` | `string` | no |
| `method` | `string` | no |
| `baseReference` | `string` | no |
| `rate` | `number` | no |
| `ceiling` | `number` | no |
| `floor` | `number` | no |
| `exemptionThreshold` | `number` | no |
| `flatAmount` | `number` | no |
| `bracketTableCode` | `string` | no |
| `lookupTableCode` | `string` | no |
| `taxable` | `boolean` | no |
| `socialContributable` | `boolean` | no |
| `countryCode` | `string` | no |
| `displayOrder` | `integer(int32)` | no |
| `active` | `boolean` | no |
| `effectiveFrom` | `string(date)` | no |
| `effectiveTo` | `string(date)` | no |

## PayVariableResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `periode` | `string` | no |
| `overtimeHoursDay` | `number` | no |
| `overtimeHoursNight` | `number` | no |
| `overtimeHoursSundayHoliday` | `number` | no |
| `bonuses` | `number` | no |
| `unpaidAbsenceDays` | `number` | no |
| `advances` | `number` | no |
| `workedDaysOverride` | `integer(int32)` | no |
| `locked` | `boolean` | no |

## PaymentScheduleRequest

| Field | Type | Required |
|---|---|---|
| `dueDate` | `string(date)` | yes |
| `amount` | `number` | yes |
| `currency` | `string` | no |

## PaymentScheduleView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `lineIndex` | `integer(int32)` | no |
| `dueDate` | `string(date)` | no |
| `amount` | `number` | no |
| `paidAmount` | `number` | no |
| `remainingAmount` | `number` | no |
| `currency` | `string` | no |
| `status` | `string` | no |
| `paidAt` | `string(date-time)` | no |

## PaymentView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `billingDocumentId` | `string(uuid)` | no |
| `invoiceId` | `string(uuid)` | no |
| `supplierInvoiceId` | `string(uuid)` | no |
| `counterparty` | `CounterpartySummaryView` | no |
| `reference` | `string` | no |
| `amount` | `number` | no |
| `currency` | `string` | no |
| `status` | `string` | no |
| `linkedServiceCode` | `string` | no |
| `linkedDocumentType` | `string` | no |
| `linkedDocumentId` | `string(uuid)` | no |
| `paidAt` | `string(date-time)` | no |

## PayrollEmployeeResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `actorId` | `string(uuid)` | no |
| `matricule` | `string` | no |
| `displayName` | `string` | no |
| `email` | `string` | no |
| `socialSecurityNo` | `string` | no |
| `categorie` | `integer(int32)` | no |
| `echelon` | `string` | no |
| `departmentCode` | `string` | no |
| `hireDate` | `string(date)` | no |
| `departureDate` | `string(date)` | no |
| `maritalStatus` | `string` | no |
| `dependentChildren` | `integer(int32)` | no |
| `baseSalary` | `number` | no |
| `benefitsInKind` | `number` | no |
| `position` | `string` | no |
| `paymentChannel` | `string` | no |
| `accountRef` | `string` | no |
| `active` | `boolean` | no |

## PayrollEntryResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `currency` | `string` | no |
| `salaireBase` | `number` | no |
| `brut` | `number` | no |
| `totalDeductions` | `number` | no |
| `incomeTax` | `number` | no |
| `employerCharges` | `number` | no |
| `net` | `number` | no |
| `paymentStatus` | `string` | no |
| `paymentChannel` | `string` | no |
| `accountRef` | `string` | no |

## PayrollOnboardingManifest

| Field | Type | Required |
|---|---|---|
| `module` | `string` | no |
| `version` | `string` | no |
| `requiredServiceCode` | `string` | no |
| `permissions` | `PermissionDescriptor[]` | no |
| `suggestedRoleTemplates` | `RoleTemplate[]` | no |
| `dataSourceModes` | `DataSourceMode[]` | no |
| `onboardingFlow` | `OnboardingFlow` | no |

## PayrollRunResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `periode` | `string` | no |
| `runType` | `string` | no |
| `status` | `string` | no |
| `currency` | `string` | no |
| `totalGross` | `number` | no |
| `totalEmployeeDeductions` | `number` | no |
| `totalIncomeTax` | `number` | no |
| `totalNet` | `number` | no |
| `totalEmployerCharges` | `number` | no |
| `nbEmployes` | `integer(int32)` | no |
| `calculatedAt` | `string(date-time)` | no |
| `validatedBy` | `string(uuid)` | no |
| `validatedAt` | `string(date-time)` | no |
| `approvedBy` | `string(uuid)` | no |
| `approvedAt` | `string(date-time)` | no |
| `paidAt` | `string(date-time)` | no |
| `closedAt` | `string(date-time)` | no |
| `rejectionReason` | `string` | no |
| `rejectedBy` | `string(uuid)` | no |
| `rejectedAt` | `string(date-time)` | no |

## PayslipLineResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `payElementCode` | `string` | no |
| `libelle` | `string` | no |
| `type` | `string` | no |
| `base` | `number` | no |
| `taux` | `number` | no |
| `montant` | `number` | no |
| `ordreAffichage` | `integer(int32)` | no |

## PermissionDescriptor

| Field | Type | Required |
|---|---|---|
| `code` | `string` | no |
| `label` | `string` | no |
| `description` | `string` | no |

## PersonalInfoResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `lieuNaissance` | `string` | no |
| `situationMatrimoniale` | `string` | no |
| `typePiece` | `string` | no |
| `numeroPiece` | `string` | no |
| `dateEmissionPiece` | `string(date)` | no |
| `niuFiscal` | `string` | no |
| `permisConduire` | `string` | no |
| `languesParlees` | `string` | no |
| `emailPersonnel` | `string` | no |
| `telephoneDomicile` | `string` | no |
| `whatsapp` | `string` | no |
| `adressePostale` | `string` | no |
| `adresseDomicile` | `string` | no |
| `ville` | `string` | no |
| `region` | `string` | no |
| `codePostal` | `string` | no |

## PhysicalLayoutSnapshot

| Field | Type | Required |
|---|---|---|
| `totalSpaces` | `integer(int32)` | no |
| `activeSpaces` | `integer(int32)` | no |
| `rootSpaces` | `integer(int32)` | no |

## PhysicalLayoutView

| Field | Type | Required |
|---|---|---|
| `totalSpaces` | `integer(int32)` | no |
| `activeSpaces` | `integer(int32)` | no |
| `rootSpaceCount` | `integer(int32)` | no |
| `tree` | `PhysicalSpaceNodeView[]` | no |

## PhysicalSpaceNodeView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `parentSpaceId` | `string(uuid)` | no |
| `code` | `string` | no |
| `name` | `string` | no |
| `spaceType` | `string` | no |
| `description` | `string` | no |
| `levelNumber` | `integer(int32)` | no |
| `capacity` | `integer(int32)` | no |
| `active` | `boolean` | no |
| `assignedResources` | `integer(int64)` | no |
| `reservedResources` | `integer(int64)` | no |
| `documentCount` | `integer(int32)` | no |
| `children` | `PhysicalSpaceNodeView[]` | no |

## PhysicalSpaceResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `parentSpaceId` | `string(uuid)` | no |
| `code` | `string` | no |
| `name` | `string` | no |
| `spaceType` | `string` | no |
| `description` | `string` | no |
| `levelNumber` | `integer(int32)` | no |
| `capacity` | `integer(int32)` | no |
| `active` | `boolean` | no |
| `children` | `PhysicalSpaceResponse[]` | no |

## PlanAccountView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `accountNumber` | `string` | no |
| `label` | `string` | no |
| `accountClass` | `string` | no |
| `active` | `boolean` | no |
| `createdAt` | `string(date-time)` | no |

## PlanCampaignRequest

| Field | Type | Required |
|---|---|---|
| `agencyId` | `string(uuid)` | no |
| `warehouseId` | `string(uuid)` | no |
| `physicalSpaceId` | `string(uuid)` | no |
| `supervisorActorId` | `string(uuid)` | no |
| `campaignCode` | `string` | yes |
| `campaignType` | `string` | yes |
| `scopeType` | `string` | yes |
| `scheduledAt` | `string(date-time)` | yes |
| `notes` | `string` | no |

## PlanResponse

| Field | Type | Required |
|---|---|---|
| `code` | `string` | no |
| `name` | `string` | no |
| `description` | `string` | no |
| `price` | `number` | no |
| `currency` | `string` | no |
| `periodDays` | `integer(int32)` | no |
| `serviceCodes` | `string[]` | no |
| `active` | `boolean` | no |

## PlanTrainingRequest

| Field | Type | Required |
|---|---|---|
| `agencyId` | `string(uuid)` | no |
| `intitule` | `string` | no |
| `organisme` | `string` | no |
| `dateDebut` | `string(date)` | no |
| `dateFin` | `string(date)` | no |
| `cout` | `number` | no |
| `nbPlaces` | `integer(int32)` | no |
| `lieu` | `string` | no |

## PointOfInterestResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `name` | `string` | no |
| `poiType` | `string` | no |
| `latitude` | `number(double)` | no |
| `longitude` | `number(double)` | no |

## PointOfInterestView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `name` | `string` | no |
| `poiType` | `string` | no |
| `latitude` | `number(double)` | no |
| `longitude` | `number(double)` | no |
| `distanceMeters` | `integer(int32)` | no |
| `linkDescription` | `string` | no |

## PointingView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `accountId` | `string(uuid)` | no |
| `entryId` | `string(uuid)` | no |
| `notes` | `string` | no |
| `createdAt` | `string(date-time)` | no |

## PolicyResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `targetType` | `string` | no |
| `documentCategory` | `string` | no |
| `mandatory` | `boolean` | no |
| `approvalRequired` | `boolean` | no |
| `expiryDays` | `integer(int32)` | no |
| `reviewerResponsibilityType` | `string` | no |

## PrepareInventoryCampaignRequest

| Field | Type | Required |
|---|---|---|
| `agencyId` | `string(uuid)` | no |
| `warehouseId` | `string(uuid)` | no |
| `physicalSpaceId` | `string(uuid)` | no |
| `supervisorActorId` | `string(uuid)` | no |
| `campaignCode` | `string` | yes |
| `campaignType` | `string` | yes |
| `scopeType` | `string` | yes |
| `scheduledAt` | `string(date-time)` | yes |
| `notes` | `string` | no |

## ProductCategoryResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `code` | `string` | no |
| `name` | `string` | no |
| `parentCode` | `string` | no |
| `description` | `string` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |

## ProductLocationResponse

| Field | Type | Required |
|---|---|---|
| `agencyId` | `string(uuid)` | no |
| `productId` | `string(uuid)` | no |
| `binCode` | `string` | no |
| `quantity` | `number` | no |
| `note` | `string` | no |
| `updatedAt` | `string(date-time)` | no |

## ProductPositionSnapshot

| Field | Type | Required |
|---|---|---|
| `productId` | `string(uuid)` | no |
| `sku` | `string` | no |
| `name` | `string` | no |
| `variantLabel` | `string` | no |
| `quantity` | `number` | no |

## ProductPositionView

| Field | Type | Required |
|---|---|---|
| `productId` | `string(uuid)` | no |
| `sku` | `string` | no |
| `name` | `string` | no |
| `variantLabel` | `string` | no |
| `onHandQuantity` | `number` | no |

## ProductPriceResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `productId` | `string(uuid)` | no |
| `priceType` | `string` | no |
| `amount` | `number` | no |
| `currency` | `string` | no |
| `effectiveFrom` | `string(date-time)` | no |

## ProductResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `sku` | `string` | no |
| `name` | `string` | no |
| `familyCode` | `string` | no |
| `categoryCode` | `string` | no |
| `variantLabel` | `string` | no |
| `barcode` | `string` | no |
| `description` | `string` | no |
| `minStockLevel` | `integer(int32)` | no |
| `maxStockLevel` | `integer(int32)` | no |
| `unitPrice` | `number` | no |
| `currency` | `string` | no |
| `status` | `string` | no |
| `cost` | `number` | no |
| `photo` | `string` | no |
| `uom` | `string` | no |
| `quantity` | `number` | no |
| `allowedSaleSizes` | `SaleSize[]` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |

## ProductSearchResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `sku` | `string` | no |
| `name` | `string` | no |
| `familyCode` | `string` | no |
| `variantLabel` | `string` | no |
| `barcode` | `string` | no |
| `description` | `string` | no |
| `unitPrice` | `number` | no |
| `currency` | `string` | no |
| `status` | `string` | no |

## ProductSpecResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `productId` | `string(uuid)` | no |
| `weightKg` | `number` | no |
| `lengthCm` | `number` | no |
| `widthCm` | `number` | no |
| `heightCm` | `number` | no |
| `materials` | `string` | no |

## ProductSummaryView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `code` | `string` | no |
| `name` | `string` | no |

## ProductTransformationResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `sourceProductId` | `string(uuid)` | no |
| `targetProductId` | `string(uuid)` | no |
| `referenceNumber` | `string` | no |
| `sourceQuantity` | `number` | no |
| `targetQuantity` | `number` | no |
| `status` | `string` | no |

## ProposedActivityResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `type` | `string` | no |
| `name` | `string` | no |
| `rate` | `number` | no |
| `description` | `string` | no |

## ProspectStats

| Field | Type | Required |
|---|---|---|
| `conversionProbability` | `integer(int32)` | no |
| `potential` | `string` | no |
| `source` | `string` | no |
| `converted` | `boolean` | no |

## ProvisionedClientApplicationResponse

| Field | Type | Required |
|---|---|---|
| `clientApplication` | `ClientApplicationResponse` | no |
| `clientSecret` | `string` | no |

## PublicOrganizationBrandingResponse

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `name` | `string` | no |
| `logoUrl` | `string` | no |

## PublicSignUpRequest

| Field | Type | Required |
|---|---|---|
| `tenantId` | `string(uuid)` | no |
| `signUpSelectionToken` | `string` | no |
| `contextId` | `string` | no |
| `firstName` | `string` | yes |
| `lastName` | `string` | yes |
| `username` | `string` | no |
| `email` | `string(email)` | yes |
| `phoneNumber` | `string` | no |
| `password` | `string` | no |
| `socialProvider` | `string` | no |
| `externalSubject` | `string` | no |
| `captchaVerificationToken` | `string` | no |
| `accountType` | `string` | no |
| `businessType` | `string` | no |
| `onboardingData` | `object` | no |

## PurchaseOrderResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `poNumber` | `string` | no |
| `supplierId` | `string(uuid)` | no |
| `supplierName` | `string` | no |
| `supplierAddress` | `string` | no |
| `status` | `string` | no |
| `notes` | `string` | no |
| `lines` | `LineItemResponse[]` | no |
| `totalHt` | `number` | no |
| `totalTva` | `number` | no |
| `totalTtc` | `number` | no |
| `retenueAir` | `number` | no |
| `retenueIr` | `number` | no |
| `netAPayer` | `number` | no |

## PurchaseRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |

## ReasonRequest

| Field | Type | Required |
|---|---|---|
| `reason` | `string` | no |

## ReceiptResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `receiptNumber` | `string` | no |
| `poId` | `string(uuid)` | no |
| `poNumber` | `string` | no |
| `agencyId` | `string(uuid)` | no |
| `supplierName` | `string` | no |
| `status` | `string` | no |
| `stockPosted` | `boolean` | no |
| `receivedAt` | `string(date)` | no |
| `note` | `string` | no |
| `lines` | `LineItemResponse[]` | no |

## ReconciliationMatchResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `statementLineId` | `string(uuid)` | no |
| `matchedEntityType` | `string` | no |
| `matchedEntityId` | `string(uuid)` | no |
| `matchDate` | `string(date-time)` | no |
| `matchType` | `string` | no |
| `matchedBy` | `string(uuid)` | no |
| `unmatchedAt` | `string(date-time)` | no |
| `active` | `boolean` | no |

## ReconciliationResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `bankAccountId` | `string(uuid)` | no |
| `statementId` | `string(uuid)` | no |
| `referenceNumber` | `string` | no |
| `status` | `string` | no |
| `closedAt` | `string(date-time)` | no |

## ReconciliationRunResult

| Field | Type | Required |
|---|---|---|
| `reconciliationId` | `string(uuid)` | no |
| `matchedTransactions` | `integer(int32)` | no |

## ReconciliationSuggestionResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `matchedEntityType` | `string` | no |
| `matchedEntityId` | `string(uuid)` | no |
| `matchedEntityReference` | `string` | no |
| `matchedEntityLabel` | `string` | no |
| `amount` | `number` | no |
| `matchedAt` | `string(date)` | no |
| `direction` | `string` | no |
| `confidenceScore` | `integer(int32)` | no |
| `matchReasons` | `string[]` | no |

## ReconciliationSummary

| Field | Type | Required |
|---|---|---|
| `statementId` | `string(uuid)` | no |
| `totalLines` | `integer(int32)` | no |
| `unmatchedCount` | `integer(int32)` | no |
| `matchedCount` | `integer(int32)` | no |
| `ignoredCount` | `integer(int32)` | no |
| `unmatchedAmount` | `number` | no |
| `matchedAmount` | `number` | no |

## RecordBankSettlementRequest

| Field | Type | Required |
|---|---|---|
| `bankAccountId` | `string(uuid)` | yes |
| `settlementNumber` | `string` | no |
| `paymentMethod` | `string` | no |
| `amount` | `number` | yes |
| `reference` | `string` | no |
| `paidAt` | `string(date-time)` | no |

## RecordCashierPaymentRequest

| Field | Type | Required |
|---|---|---|
| `amount` | `number` | yes |
| `sessionId` | `string(uuid)` | no |
| `registerId` | `string(uuid)` | no |
| `reference` | `string` | no |
| `syncAccountingSettlement` | `boolean` | no |

## RecordLocationObservationRequest

| Field | Type | Required |
|---|---|---|
| `latitude` | `number(double)` | yes |
| `longitude` | `number(double)` | yes |

## RecordMaintenanceRequest

| Field | Type | Required |
|---|---|---|
| `maintenanceType` | `string` | yes |
| `description` | `string` | yes |
| `status` | `string` | no |

## RecordNetworkObservationRequest

| Field | Type | Required |
|---|---|---|
| `ipAddress` | `string` | no |
| `macAddress` | `string` | no |

## RecordStockMovementRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `agencyId` | `string(uuid)` | yes |
| `productId` | `string(uuid)` | yes |
| `thirdPartyId` | `string(uuid)` | no |
| `referenceNumber` | `string` | no |
| `sourceDocumentType` | `string` | no |
| `sourceDocumentNumber` | `string` | no |
| `movementType` | `string` | yes |
| `quantity` | `number` | yes |

## RecordTransformationRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `agencyId` | `string(uuid)` | yes |
| `sourceProductId` | `string(uuid)` | yes |
| `targetProductId` | `string(uuid)` | yes |
| `referenceNumber` | `string` | no |
| `sourceQuantity` | `number` | yes |
| `targetQuantity` | `number` | yes |

## RefreshTokenRequest

| Field | Type | Required |
|---|---|---|
| `refreshToken` | `string` | yes |

## RefreshTokenResponse

| Field | Type | Required |
|---|---|---|
| `accessToken` | `string` | no |
| `refreshToken` | `string` | no |
| `tokenType` | `string` | no |
| `accessExpiresInSeconds` | `integer(int64)` | no |
| `refreshExpiresInSeconds` | `integer(int64)` | no |
| `refreshExpiresAt` | `string(date-time)` | no |

## RegisterAccountConnectorTypeRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `name` | `string` | yes |

## RegisterAccountSubTypeRequest

| Field | Type | Required |
|---|---|---|
| `accountTypeId` | `string(uuid)` | yes |
| `code` | `string` | yes |
| `libelle` | `string` | yes |
| `description` | `string` | no |
| `peutEmettreCheques` | `boolean` | no |
| `peutRecevoirCheques` | `boolean` | no |
| `peutTransactionsEspeces` | `boolean` | no |
| `decouvertAutorise` | `boolean` | no |
| `ordreAffichage` | `integer(int32)` | no |

## RegisterAccountTypeRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `libelle` | `string` | yes |
| `description` | `string` | no |
| `peutEmettreCheques` | `boolean` | no |
| `peutRecevoirCheques` | `boolean` | no |
| `peutTransactionsEspeces` | `boolean` | no |
| `decouvertAutorise` | `boolean` | no |
| `decouvertParDefaut` | `number` | no |
| `ordreAffichage` | `integer(int32)` | no |

## RegisterBankAccountRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `bankThirdPartyId` | `string(uuid)` | no |
| `ownerThirdPartyId` | `string(uuid)` | no |
| `bankName` | `string` | yes |
| `accountNumber` | `string` | yes |
| `iban` | `string` | yes |
| `currency` | `string` | yes |

## RegisterBankRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `name` | `string` | yes |
| `swiftCode` | `string` | no |
| `bankCode` | `string` | no |
| `country` | `string` | no |
| `address` | `string` | no |
| `bankCategoryId` | `string(uuid)` | no |

## RegisterBankStatementRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `bankAccountId` | `string(uuid)` | yes |
| `statementNumber` | `string` | no |
| `statementDate` | `string(date)` | yes |
| `openingBalance` | `number` | yes |
| `closingBalance` | `number` | yes |

## RegisterBankTransactionRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `bankAccountId` | `string(uuid)` | yes |
| `referenceNumber` | `string` | no |
| `transactionType` | `string` | yes |
| `transactionDate` | `string(date)` | yes |
| `amount` | `number` | yes |
| `description` | `string` | yes |

## RegisterCheckPaymentRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `bankAccountId` | `string(uuid)` | yes |
| `checkType` | `string` | no |
| `checkNumber` | `string` | no |
| `amount` | `number` | yes |
| `currency` | `string` | no |
| `partnerName` | `string` | no |
| `partnerId` | `string(uuid)` | no |
| `issueDate` | `string(date)` | no |
| `dueDate` | `string(date)` | no |
| `receiptDate` | `string(date)` | no |
| `issuerBank` | `string` | no |
| `imageUrl` | `string` | no |
| `description` | `string` | no |
| `checkbookId` | `string(uuid)` | no |

## RegisterCheckbookRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `bankAccountId` | `string(uuid)` | yes |
| `prefix` | `string` | yes |
| `type` | `string` | no |
| `startNumber` | `integer(int64)` | no |
| `numberOfPages` | `integer(int32)` | no |

## RegisterExternalServiceRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | no |
| `displayName` | `string` | no |
| `description` | `string` | no |

## RegisterInvoiceSettlementRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `bankAccountId` | `string(uuid)` | yes |
| `invoiceId` | `string(uuid)` | yes |
| `settlementNumber` | `string` | no |
| `paymentMethod` | `string` | yes |
| `amount` | `number` | yes |

## RegisterMaterialResourceRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `agencyId` | `string(uuid)` | yes |
| `resourceCode` | `string` | yes |
| `name` | `string` | yes |
| `category` | `string` | yes |
| `serialNumber` | `string` | yes |
| `latitude` | `number(double)` | no |
| `longitude` | `number(double)` | no |
| `ipAddress` | `string` | no |
| `macAddress` | `string` | no |

## RegisterTransactionTypeRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `label` | `string` | yes |
| `inbound` | `boolean` | no |
| `category` | `string` | no |
| `description` | `string` | no |

## RegisterUserRequest

| Field | Type | Required |
|---|---|---|
| `actorId` | `string(uuid)` | no |
| `username` | `string` | yes |
| `email` | `string(email)` | yes |
| `phoneNumber` | `string` | no |
| `password` | `string` | no |
| `authProvider` | `string` | yes |
| `externalSubject` | `string` | no |

## RegularOpeningHoursRequest

| Field | Type | Required |
|---|---|---|
| `rules` | `UpsertOpeningHoursRequest[]` | yes |

## RejectLeaveRequest

| Field | Type | Required |
|---|---|---|
| `commentaire` | `string` | no |

## RejectLoanRequest

| Field | Type | Required |
|---|---|---|
| `motif` | `string` | no |

## RejectMaterialRequestRequest

| Field | Type | Required |
|---|---|---|
| `reason` | `string` | no |

## RejectRunRequest

| Field | Type | Required |
|---|---|---|
| `reason` | `string` | no |

## RejectTimesheetRequest

| Field | Type | Required |
|---|---|---|
| `comment` | `string` | no |

## RejectTrainingRequestRequest

| Field | Type | Required |
|---|---|---|
| `reason` | `string` | yes |

## RenewContractRequest

| Field | Type | Required |
|---|---|---|
| `newDateFin` | `string(date)` | no |

## ReplaceRolePermissionsRequest

| Field | Type | Required |
|---|---|---|
| `permissions` | `string[]` | yes |

## ReportExportView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `reportType` | `string` | no |
| `format` | `string` | no |
| `status` | `string` | no |
| `generatedAt` | `string(date-time)` | no |
| `content` | `string` | no |

## ReportItemDto

| Field | Type | Required |
|---|---|---|
| `code` | `string` | no |
| `description` | `string` | no |
| `debit` | `number` | no |
| `credit` | `number` | no |
| `solde` | `number` | no |

## ReportLineItem

| Field | Type | Required |
|---|---|---|
| `accountNumber` | `string` | no |
| `label` | `string` | no |
| `balance` | `number` | no |

## RequestLoanAdvanceRequest

| Field | Type | Required |
|---|---|---|
| `employeeId` | `string(uuid)` | no |
| `montant` | `number` | no |
| `nbEcheances` | `integer(int32)` | no |
| `motif` | `string` | no |

## ReserveMaterialResourceRequest

| Field | Type | Required |
|---|---|---|
| `reserveeType` | `string` | yes |
| `reserveeId` | `string(uuid)` | yes |
| `reason` | `string` | yes |

## ResetPasswordRequest

| Field | Type | Required |
|---|---|---|
| `resetToken` | `string` | yes |
| `newPassword` | `string` | yes |

## ResourceAssignmentResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `resourceId` | `string(uuid)` | no |
| `assigneeType` | `string` | no |
| `assigneeId` | `string(uuid)` | no |
| `assignedAt` | `string(date-time)` | no |
| `status` | `string` | no |
| `unassignedAt` | `string(date-time)` | no |

## ResourceLocationObservationResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `resourceId` | `string(uuid)` | no |
| `latitude` | `number(double)` | no |
| `longitude` | `number(double)` | no |
| `observedAt` | `string(date-time)` | no |

## ResourceNetworkObservationResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `resourceId` | `string(uuid)` | no |
| `ipAddress` | `string` | no |
| `macAddress` | `string` | no |
| `observedAt` | `string(date-time)` | no |

## ResourceReservationResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `resourceId` | `string(uuid)` | no |
| `reserveeType` | `string` | no |
| `reserveeId` | `string(uuid)` | no |
| `reason` | `string` | no |
| `reservedAt` | `string(date-time)` | no |
| `status` | `string` | no |
| `releasedAt` | `string(date-time)` | no |

## RetireAssetRequest

| Field | Type | Required |
|---|---|---|
| `notes` | `string` | yes |

## RetroactiveResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `originPeriod` | `string` | no |
| `targetPeriod` | `string` | no |
| `reason` | `string` | no |
| `currency` | `string` | no |
| `oldGross` | `number` | no |
| `newGross` | `number` | no |
| `deltaGross` | `number` | no |
| `oldNet` | `number` | no |
| `newNet` | `number` | no |
| `deltaNet` | `number` | no |
| `status` | `string` | no |

## ReviewDocumentRequest

| Field | Type | Required |
|---|---|---|
| `reviewStatus` | `string` | yes |
| `expiresAt` | `string(date-time)` | no |
| `notes` | `string` | no |

## ReviewReconciliationRequest

| Field | Type | Required |
|---|---|---|
| `review` | `string` | yes |

## ReviewResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `evaluateurPartyId` | `string(uuid)` | no |
| `evaluateurDisplayName` | `string` | no |
| `periode` | `string` | no |
| `noteGlobale` | `number` | no |
| `commentaires` | `string` | no |
| `planAction` | `string` | no |
| `status` | `string` | no |

## RhKpiSnapshotResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `periode` | `string` | no |
| `effectifTotal` | `integer(int32)` | no |
| `effectifActif` | `integer(int32)` | no |
| `tauxTurnover` | `number` | no |
| `tauxAbsenteisme` | `number` | no |
| `masseSalariale` | `number` | no |
| `couvertureCompetences` | `number` | no |

## RoleResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `code` | `string` | no |
| `name` | `string` | no |
| `scopeType` | `string` | no |
| `permissions` | `string[]` | no |

## RoleTemplate

| Field | Type | Required |
|---|---|---|
| `code` | `string` | no |
| `name` | `string` | no |
| `description` | `string` | no |
| `scopeType` | `string` | no |
| `permissions` | `string[]` | no |

## RotateClientApplicationSecretRequest

| Field | Type | Required |
|---|---|---|
| `clientSecret` | `string` | no |

## RunPayrollRequest

| Field | Type | Required |
|---|---|---|
| `period` | `string` | no |
| `agencyId` | `string(uuid)` | no |
| `runType` | `string` | no |

## SaleSize

| Field | Type | Required |
|---|---|---|
| `size` | `string` | no |
| `unitPrice` | `number` | no |
| `unitPriceWithTax` | `number` | no |
| `minQuantity` | `number` | no |
| `active` | `boolean` | no |
| `isNegotiable` | `boolean` | no |
| `minNegotiationPercentage` | `number` | no |

## SalesOrderLineResponse

| Field | Type | Required |
|---|---|---|
| `productId` | `string(uuid)` | no |
| `quantity` | `number` | no |
| `unitPrice` | `number` | no |
| `lineAmount` | `number` | no |

## SalesOrderResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `customerThirdPartyId` | `string(uuid)` | no |
| `productId` | `string(uuid)` | no |
| `orderNumber` | `string` | no |
| `quantity` | `number` | no |
| `unitPrice` | `number` | no |
| `totalQuantity` | `number` | no |
| `subtotalAmount` | `number` | no |
| `totalAmount` | `number` | no |
| `currency` | `string` | no |
| `status` | `string` | no |
| `lines` | `SalesOrderLineResponse[]` | no |

## SaveClientApplicationPlanRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | no |
| `displayName` | `string` | no |
| `description` | `string` | no |
| `allowedServices` | `string[]` | no |

## SaveCommercialPlanRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | no |
| `displayName` | `string` | no |
| `description` | `string` | no |
| `targetType` | `string` | no |
| `packCodes` | `string[]` | no |
| `serviceCodes` | `string[]` | no |
| `compatibleAddOnCodes` | `string[]` | no |
| `serviceQuotas` | `CommercialServiceQuota[]` | no |

## SavePlanRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | no |
| `name` | `string` | no |
| `description` | `string` | no |
| `price` | `number` | no |
| `currency` | `string` | no |
| `periodDays` | `integer(int32)` | no |
| `serviceCodes` | `string[]` | no |
| `active` | `boolean` | no |

## SavePreferenceRequest

| Field | Type | Required |
|---|---|---|
| `userId` | `string(uuid)` | yes |
| `channel` | `string` | yes |
| `enabled` | `boolean` | no |
| `locale` | `string` | no |

## SaveProviderRequest

| Field | Type | Required |
|---|---|---|
| `channel` | `string` | yes |
| `type` | `string` | yes |
| `name` | `string` | yes |
| `defaultProvider` | `boolean` | no |
| `active` | `boolean` | no |
| `configurationJson` | `string` | no |

## SaveReminderRequest

| Field | Type | Required |
|---|---|---|
| `templateCode` | `string` | yes |
| `channel` | `string` | yes |
| `recipientUserId` | `string(uuid)` | no |
| `recipientAddress` | `string` | no |
| `dueAt` | `string(date-time)` | yes |
| `active` | `boolean` | no |
| `variables` | `object` | no |
| `metadata` | `object` | no |

## SaveTemplateRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `channel` | `string` | yes |
| `locale` | `string` | no |
| `subjectTemplate` | `string` | no |
| `bodyTemplate` | `string` | yes |
| `active` | `boolean` | no |

## ScheduleInterviewRequest

| Field | Type | Required |
|---|---|---|
| `applicationId` | `string(uuid)` | no |
| `type` | `string` | no |
| `dateHeure` | `string(date-time)` | no |
| `lieu` | `string` | no |
| `interviewerPartyId` | `string(uuid)` | no |
| `interviewerDisplayName` | `string` | no |

## SelectLoginContextRequest

| Field | Type | Required |
|---|---|---|
| `selectionToken` | `string` | yes |
| `contextId` | `string` | yes |
| `organizationId` | `string(uuid)` | no |

## SelectableSignUpContextResponse

| Field | Type | Required |
|---|---|---|
| `contextId` | `string` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `organizationCode` | `string` | no |
| `organizationName` | `string` | no |
| `organizationType` | `string` | no |

## SellArtworkRequest

| Field | Type | Required |
|---|---|---|
| `customerThirdPartyId` | `string(uuid)` | yes |
| `agencyId` | `string(uuid)` | no |
| `quantity` | `number` | yes |
| `unitPrice` | `number` | yes |
| `currency` | `string` | yes |
| `orderNumber` | `string` | yes |

## SendNotificationRequest

| Field | Type | Required |
|---|---|---|
| `recipientUserId` | `string(uuid)` | no |
| `recipientAddress` | `string` | no |
| `channel` | `string` | yes |
| `templateCode` | `string` | no |
| `subject` | `string` | no |
| `body` | `string` | yes |
| `variables` | `object` | no |
| `metadata` | `object` | no |

## ServiceEntitlement

| Field | Type | Required |
|---|---|---|
| `code` | `string` | no |
| `displayName` | `string` | no |
| `description` | `string` | no |

## ServiceWorkspaceView

| Field | Type | Required |
|---|---|---|
| `workspaceCode` | `string` | no |
| `displayName` | `string` | no |
| `description` | `string` | no |
| `organizationId` | `string(uuid)` | no |
| `requiredServiceCodes` | `string[]` | no |
| `subscribedServiceCodes` | `string[]` | no |
| `missingServiceCodes` | `string[]` | no |
| `activeAgencyCount` | `integer(int32)` | no |
| `activeOperationalAgencyCount` | `integer(int32)` | no |
| `activeWarehouseCount` | `integer(int32)` | no |
| `assets` | `WorkspaceAssetSummary` | no |
| `inventory` | `WorkspaceInventorySummary` | no |
| `physicalLayout` | `WorkspacePhysicalLayoutSummary` | no |
| `documents` | `WorkspaceDocumentSummary` | no |
| `readiness` | `WorkspaceReadiness` | no |

## SetEmployeePhotoRequest

| Field | Type | Required |
|---|---|---|
| `fileId` | `string(uuid)` | no |

## SharedSsoSessionResponse

| Field | Type | Required |
|---|---|---|
| `token` | `string` | no |
| `tokenType` | `string` | no |
| `expiresInSeconds` | `integer(int64)` | no |

## SignPayloadRequest

| Field | Type | Required |
|---|---|---|
| `privateKey` | `string` | no |
| `payload` | `string` | no |

## SignatureResponse

| Field | Type | Required |
|---|---|---|
| `signature` | `string` | no |

## SigningPayloadResponse

| Field | Type | Required |
|---|---|---|
| `payload` | `string` | no |

## SiteDocumentSnapshot

| Field | Type | Required |
|---|---|---|
| `totalDocuments` | `integer(int32)` | no |
| `countsByCategory` | `object` | no |

## SiteDocumentView

| Field | Type | Required |
|---|---|---|
| `totalDocuments` | `integer(int32)` | no |
| `countsByCategory` | `object` | no |

## SkillResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `name` | `string` | no |
| `categorie` | `string` | no |
| `description` | `string` | no |

## SocialDeclarationResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `type` | `string` | no |
| `periode` | `string` | no |
| `format` | `string` | no |
| `statut` | `string` | no |
| `fichierId` | `string(uuid)` | no |
| `generatedAt` | `string(date-time)` | no |
| `submittedAt` | `string(date-time)` | no |

## StartClosingRunRequest

| Field | Type | Required |
|---|---|---|
| `periodLabel` | `string` | yes |

## StartSynchronizationJobRequest

| Field | Type | Required |
|---|---|---|
| `domain` | `string` | yes |

## StatementLineResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `statementId` | `string(uuid)` | no |
| `lineNumber` | `integer(int32)` | no |
| `reference` | `string` | no |
| `transactionDate` | `string(date)` | no |
| `valueDate` | `string(date)` | no |
| `amount` | `number` | no |
| `direction` | `string` | no |
| `description` | `string` | no |
| `partnerName` | `string` | no |
| `partnerAccount` | `string` | no |
| `balanceAfter` | `number` | no |
| `reconciliationStatus` | `string` | no |
| `ignoredReason` | `string` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |

## StatementLineView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `statementId` | `string(uuid)` | no |
| `reference` | `string` | no |
| `amount` | `number` | no |
| `currency` | `string` | no |
| `direction` | `string` | no |
| `status` | `string` | no |
| `reconciled` | `boolean` | no |
| `createdAt` | `string(date-time)` | no |

## StatementUploadResponse

| Field | Type | Required |
|---|---|---|
| `statement` | `BankStatementResponse` | no |
| `linesImported` | `integer(int32)` | no |
| `lines` | `StatementLineResponse[]` | no |

## StockBalanceResponse

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `productId` | `string(uuid)` | no |
| `onHandQuantity` | `number` | no |

## StockMovementPostingView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `movementReference` | `string` | no |
| `movementType` | `string` | no |
| `valuationAmount` | `number` | no |
| `currency` | `string` | no |
| `createdAt` | `string(date-time)` | no |

## StockMovementResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `productId` | `string(uuid)` | no |
| `thirdPartyId` | `string(uuid)` | no |
| `referenceNumber` | `string` | no |
| `sourceDocumentType` | `string` | no |
| `sourceDocumentNumber` | `string` | no |
| `movementType` | `string` | no |
| `quantity` | `number` | no |
| `status` | `string` | no |

## StoredFile

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `publicUrl` | `string` | no |
| `fileName` | `string` | no |
| `contentType` | `string` | no |
| `size` | `integer(int64)` | no |
| `createdAt` | `string(date-time)` | no |

## StoredFileResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `uploadedByUserId` | `string(uuid)` | no |
| `fileName` | `string` | no |
| `contentType` | `string` | no |
| `size` | `integer(int64)` | no |
| `documentType` | `string` | no |
| `analysisStatus` | `string` | no |
| `analysisReason` | `string` | no |

## SubmitCampaignRequest

| Field | Type | Required |
|---|---|---|
| `variancePercent` | `number` | yes |

## SubmitLeaveRequest

| Field | Type | Required |
|---|---|---|
| `employeeId` | `string(uuid)` | no |
| `type` | `string` | no |
| `dateDebut` | `string(date)` | no |
| `dateFin` | `string(date)` | no |
| `motif` | `string` | no |
| `justificatifFileId` | `string(uuid)` | no |

## SubmitMyCertificateRequest

| Field | Type | Required |
|---|---|---|
| `typeCertificat` | `string` | yes |
| `dateEmission` | `string(date)` | yes |
| `dateExpiration` | `string(date)` | yes |
| `fichierId` | `string(uuid)` | no |

## SubmitReviewRequest

| Field | Type | Required |
|---|---|---|
| `noteGlobale` | `number` | no |
| `commentaires` | `string` | no |
| `planAction` | `string` | no |

## SubscribeOrganizationServiceRequest

| Field | Type | Required |
|---|---|---|
| `serviceCode` | `string` | yes |
| `requestQuotaLimit` | `integer(int64)` | no |
| `requestQuotaWindowSeconds` | `integer(int64)` | no |

## SubscriptionResponse

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `planCode` | `string` | no |
| `paidAt` | `string(date-time)` | no |
| `paidUntil` | `string(date-time)` | no |
| `active` | `boolean` | no |

## SupplierStats

| Field | Type | Required |
|---|---|---|
| `paymentMode` | `string` | no |
| `mainProductType` | `string` | no |
| `deliveryLeadTime` | `string` | no |
| `certification` | `string` | no |

## SuspendRequest

| Field | Type | Required |
|---|---|---|
| `reason` | `string` | no |

## SyncDocumentToAccountingRequest

| Field | Type | Required |
|---|---|---|
| `invoiceNumber` | `string` | no |
| `postInvoice` | `boolean` | no |

## SynchronizationJobView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `domain` | `string` | no |
| `status` | `string` | no |
| `startedAt` | `string(date-time)` | no |
| `completedAt` | `string(date-time)` | no |
| `summary` | `string` | no |

## SystemAuditEntry

Type: `object`

## SystemAuditResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `actorUserId` | `string(uuid)` | no |
| `action` | `string` | no |
| `targetType` | `string` | no |
| `targetId` | `string` | no |
| `payloadSummary` | `string` | no |
| `createdAt` | `string(date-time)` | no |
| `requestId` | `string` | no |
| `clientApplicationId` | `string` | no |
| `remoteIp` | `string` | no |
| `httpMethod` | `string` | no |
| `httpPath` | `string` | no |
| `integrityHash` | `string` | no |

## TargetView

| Field | Type | Required |
|---|---|---|
| `targetType` | `string` | no |
| `targetId` | `string(uuid)` | no |
| `at` | `string(date-time)` | no |
| `status` | `string` | no |

## Tax

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `code` | `string` | no |
| `name` | `string` | no |
| `rate` | `number` | no |
| `accountingAccount` | `string` | no |
| `active` | `boolean` | no |

## TaxBracketTableResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `code` | `string` | no |
| `label` | `string` | no |
| `countryCode` | `string` | no |
| `effectiveFrom` | `string(date)` | no |
| `effectiveTo` | `string(date)` | no |
| `active` | `boolean` | no |
| `brackets` | `BracketLine[]` | no |

## TaxDeclarationView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `taxType` | `string` | no |
| `periodLabel` | `string` | no |
| `taxableBase` | `number` | no |
| `taxAmount` | `number` | no |
| `status` | `string` | no |
| `createdAt` | `string(date-time)` | no |
| `submittedAt` | `string(date-time)` | no |

## TaxDefinitionView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `code` | `string` | no |
| `label` | `string` | no |
| `rate` | `number` | no |
| `active` | `boolean` | no |
| `createdAt` | `string(date-time)` | no |

## TerminateContractRequest

| Field | Type | Required |
|---|---|---|
| `motif` | `string` | no |

## TerminateEmployeeRequest

| Field | Type | Required |
|---|---|---|
| `terminationDate` | `string(date)` | no |
| `reason` | `string` | no |

## ThirdParty

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `partyRef` | `PartyRef` | no |
| `code` | `string` | no |
| `roles` | `string[]` | no |
| `prospect` | `boolean` | no |
| `segment` | `string` | no |
| `qualificationScore` | `integer(int32)` | no |
| `lastContactedAt` | `string(date-time)` | no |
| `nextFollowUpAt` | `string(date-time)` | no |
| `followUpStatus` | `string` | no |
| `active` | `boolean` | no |
| `convertedAt` | `string(date-time)` | no |
| `type` | `string` | no |
| `legalForm` | `string` | no |
| `uniqueIdentificationNumber` | `string` | no |
| `tradeRegistrationNumber` | `string` | no |
| `name` | `string` | no |
| `acronym` | `string` | no |
| `longName` | `string` | no |
| `logoUri` | `string` | no |
| `logoId` | `string(uuid)` | no |
| `accountingAccountNumbers` | `string[]` | no |
| `authorizedPaymentMethods` | `string[]` | no |
| `authorizedCreditLimit` | `number` | no |
| `maxDiscountRate` | `number` | no |
| `vatSubject` | `boolean` | no |
| `operationsBalance` | `number` | no |
| `openingBalance` | `number` | no |
| `payTermNumber` | `integer(int32)` | no |
| `payTermType` | `string` | no |
| `thirdPartyFamily` | `string` | no |
| `classification` | `string` | no |
| `taxNumber` | `string` | no |
| `loyaltyPoints` | `integer(int32)` | no |
| `loyaltyPointsUsed` | `integer(int32)` | no |
| `loyaltyPointsExpired` | `integer(int32)` | no |
| `deletedAt` | `string(date-time)` | no |
| `shortName` | `string` | no |
| `description` | `string` | no |
| `accountingAccount` | `string` | no |
| `bankAccountNumber` | `string` | no |
| `tradeRegistryNumber` | `string` | no |
| `vatNumber` | `string` | no |
| `businessSector` | `string` | no |
| `companySize` | `string` | no |
| `email` | `string` | no |
| `phoneNumber` | `string` | no |
| `website` | `string` | no |
| `preferredChannel` | `string` | no |
| `logoUrl` | `string` | no |
| `notes` | `string` | no |
| `fax` | `string` | no |
| `contact` | `string` | no |
| `nui` | `string` | no |
| `formeJuridique` | `string` | no |
| `address` | `string` | no |
| `addressComplement` | `string` | no |
| `postalCode` | `string` | no |
| `city` | `string` | no |
| `country` | `string` | no |
| `linkedMemberId` | `string(uuid)` | no |
| `linkedOrganizationId` | `string(uuid)` | no |
| `internal` | `boolean` | no |

## ThirdPartyBankAccountRequest

| Field | Type | Required |
|---|---|---|
| `label` | `string` | yes |
| `bankName` | `string` | yes |
| `iban` | `string` | yes |
| `swiftBic` | `string` | no |
| `currency` | `string` | no |
| `primary` | `boolean` | no |

## ThirdPartyBankAccountResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `thirdPartyId` | `string(uuid)` | no |
| `label` | `string` | no |
| `bankName` | `string` | no |
| `iban` | `string` | no |
| `swiftBic` | `string` | no |
| `currency` | `string` | no |
| `primary` | `boolean` | no |

## ThirdPartyFollowUpRequest

| Field | Type | Required |
|---|---|---|
| `contactedAt` | `string(date-time)` | no |
| `nextFollowUpAt` | `string(date-time)` | no |

## ThirdPartyQualificationRequest

| Field | Type | Required |
|---|---|---|
| `segment` | `string` | no |
| `qualificationScore` | `integer(int32)` | no |

## ThirdPartyResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `partyType` | `string` | no |
| `partyId` | `string(uuid)` | no |
| `code` | `string` | no |
| `enabled` | `boolean` | no |
| `referenceCode` | `string` | no |
| `displayName` | `string` | no |
| `roles` | `string[]` | no |
| `prospect` | `boolean` | no |
| `accountingAccount` | `string` | no |
| `segment` | `string` | no |
| `qualificationScore` | `integer(int32)` | no |
| `lastContactedAt` | `string(date-time)` | no |
| `nextFollowUpAt` | `string(date-time)` | no |
| `followUpStatus` | `string` | no |
| `active` | `boolean` | no |
| `convertedAt` | `string(date-time)` | no |
| `type` | `string` | no |
| `legalForm` | `string` | no |
| `uniqueIdentificationNumber` | `string` | no |
| `tradeRegistrationNumber` | `string` | no |
| `name` | `string` | no |
| `acronym` | `string` | no |
| `longName` | `string` | no |
| `logoUri` | `string` | no |
| `logoId` | `string(uuid)` | no |
| `accountingAccountNumbers` | `string[]` | no |
| `authorizedPaymentMethods` | `string[]` | no |
| `authorizedCreditLimit` | `number` | no |
| `maxDiscountRate` | `number` | no |
| `vatSubject` | `boolean` | no |
| `operationsBalance` | `number` | no |
| `openingBalance` | `number` | no |
| `payTermNumber` | `integer(int32)` | no |
| `payTermType` | `string` | no |
| `thirdPartyFamily` | `string` | no |
| `classification` | `string` | no |
| `taxNumber` | `string` | no |
| `loyaltyPoints` | `integer(int32)` | no |
| `loyaltyPointsUsed` | `integer(int32)` | no |
| `loyaltyPointsExpired` | `integer(int32)` | no |
| `deletedAt` | `string(date-time)` | no |

## ThirdPartySearchResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `partyType` | `string` | no |
| `partyId` | `string(uuid)` | no |
| `code` | `string` | no |
| `name` | `string` | no |
| `type` | `string` | no |
| `longName` | `string` | no |
| `roles` | `string[]` | no |
| `prospect` | `boolean` | no |
| `segment` | `string` | no |
| `qualificationScore` | `integer(int32)` | no |
| `enabled` | `boolean` | no |
| `followUpStatus` | `string` | no |
| `referenceCode` | `string` | no |
| `displayName` | `string` | no |
| `active` | `boolean` | no |

## ThirdPartyStatisticsResponse

| Field | Type | Required |
|---|---|---|
| `totalCount` | `integer(int64)` | no |
| `activeCount` | `integer(int64)` | no |
| `inactiveCount` | `integer(int64)` | no |
| `prospectCount` | `integer(int64)` | no |
| `convertedCount` | `integer(int64)` | no |
| `withBankAccountCount` | `integer(int64)` | no |

## ThirdPartySummaryView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `code` | `string` | no |
| `name` | `string` | no |
| `type` | `string` | no |
| `enabled` | `boolean` | no |
| `accountingAccount` | `string` | no |
| `accountingAccountNumbers` | `string[]` | no |

## TierDocument

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tierId` | `string(uuid)` | no |
| `documentType` | `string` | no |
| `label` | `string` | no |
| `storedFileId` | `string(uuid)` | no |
| `file` | `StoredFile` | no |
| `uploadedBy` | `string(uuid)` | no |
| `uploadedAt` | `string(date-time)` | no |

## TierImportError

| Field | Type | Required |
|---|---|---|
| `rowNumber` | `integer(int32)` | no |
| `field` | `string` | no |
| `message` | `string` | no |
| `rawValue` | `string` | no |

## TierImportResult

| Field | Type | Required |
|---|---|---|
| `totalRows` | `integer(int32)` | no |
| `created` | `integer(int32)` | no |
| `skipped` | `integer(int32)` | no |
| `errors` | `TierImportError[]` | no |

## TierPortalStatistics

| Field | Type | Required |
|---|---|---|
| `tierId` | `string(uuid)` | no |
| `tierName` | `string` | no |
| `role` | `string` | no |
| `active` | `boolean` | no |
| `hasEmail` | `boolean` | no |
| `hasPhoneNumber` | `boolean` | no |
| `hasAddress` | `boolean` | no |
| `hasBankAccount` | `boolean` | no |
| `hasAccountingAccount` | `boolean` | no |
| `hasLogoUrl` | `boolean` | no |
| `profileCompletenessPercent` | `integer(int32)` | no |
| `bankAccountCount` | `integer(int32)` | no |
| `memberSince` | `string(date-time)` | no |
| `lastUpdated` | `string(date-time)` | no |
| `organizationName` | `string` | no |
| `organizationLogoUrl` | `string` | no |
| `organizationId` | `string(uuid)` | no |
| `customerStats` | `CustomerStats` | no |
| `supplierStats` | `SupplierStats` | no |
| `prospectStats` | `ProspectStats` | no |
| `agentStats` | `AgentStats` | no |

## TimelineEntryView

| Field | Type | Required |
|---|---|---|
| `occurredAt` | `string(date-time)` | no |
| `sourceType` | `string` | no |
| `domainType` | `string` | no |
| `action` | `string` | no |
| `aggregateType` | `string` | no |
| `aggregateId` | `string` | no |
| `summary` | `string` | no |

## TimelineEventResponse

| Field | Type | Required |
|---|---|---|
| `type` | `string` | no |
| `date` | `string(date)` | no |
| `title` | `string` | no |
| `detail` | `string` | no |

## TimesheetResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `periode` | `string` | no |
| `heuresNormales` | `number` | no |
| `heuresSupplementaires` | `number` | no |
| `heuresNuit` | `number` | no |
| `heuresWeekend` | `number` | no |
| `absencesNonJustifiees` | `number` | no |
| `status` | `string` | no |
| `rejectionComment` | `string` | no |

## TrainingBudgetResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `annee` | `integer(int32)` | no |
| `montantAlloue` | `number` | no |
| `montantEngage` | `number` | no |
| `montantRealise` | `number` | no |

## TrainingRequestRequest

| Field | Type | Required |
|---|---|---|
| `trainingId` | `string(uuid)` | yes |
| `employeeId` | `string(uuid)` | yes |
| `motivation` | `string` | no |

## TrainingRequestResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `employeeId` | `string(uuid)` | no |
| `trainingId` | `string(uuid)` | no |
| `motivation` | `string` | no |
| `status` | `string` | no |
| `decisionReason` | `string` | no |
| `enrollmentId` | `string(uuid)` | no |
| `decidedAt` | `string(date-time)` | no |

## TrainingResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `intitule` | `string` | no |
| `organisme` | `string` | no |
| `dateDebut` | `string(date)` | no |
| `dateFin` | `string(date)` | no |
| `cout` | `number` | no |
| `nbPlaces` | `integer(int32)` | no |
| `lieu` | `string` | no |
| `status` | `string` | no |

## TransactionRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `chainCode` | `string` | no |
| `transactionType` | `string` | no |
| `sourceService` | `string` | no |
| `sourceReference` | `string` | no |
| `payload` | `string` | no |
| `payloadHash` | `string` | no |
| `senderPublicKey` | `string` | no |
| `signature` | `string` | no |

## TransactionResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `chainCode` | `string` | no |
| `transactionType` | `string` | no |
| `sourceService` | `string` | no |
| `sourceReference` | `string` | no |
| `payloadHash` | `string` | no |
| `senderFingerprint` | `string` | no |
| `transactionHash` | `string` | no |
| `status` | `string` | no |
| `blockId` | `string(uuid)` | no |
| `blockHeight` | `integer(int64)` | no |
| `createdAt` | `string(date-time)` | no |
| `minedAt` | `string(date-time)` | no |

## TransactionTypeResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `code` | `string` | no |
| `label` | `string` | no |
| `inbound` | `boolean` | no |
| `category` | `string` | no |
| `description` | `string` | no |
| `active` | `boolean` | no |

## TransactionTypeView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `code` | `string` | no |
| `label` | `string` | no |
| `inbound` | `boolean` | no |
| `createdAt` | `string(date-time)` | no |

## TransferRequest

| Field | Type | Required |
|---|---|---|
| `sourceAccountId` | `string(uuid)` | yes |
| `targetAccountId` | `string(uuid)` | yes |
| `amount` | `number` | yes |
| `reference` | `string` | yes |

## TrialBalance

| Field | Type | Required |
|---|---|---|
| `startDate` | `string(date)` | no |
| `endDate` | `string(date)` | no |
| `lines` | `TrialBalanceLine[]` | no |
| `totalMovementDebit` | `number` | no |
| `totalMovementCredit` | `number` | no |

## TrialBalanceLine

| Field | Type | Required |
|---|---|---|
| `accountNumber` | `string` | no |
| `label` | `string` | no |
| `openingDebit` | `number` | no |
| `openingCredit` | `number` | no |
| `movementDebit` | `number` | no |
| `movementCredit` | `number` | no |
| `closingDebit` | `number` | no |
| `closingCredit` | `number` | no |

## UpdateAccountRequest

| Field | Type | Required |
|---|---|---|
| `label` | `string` | yes |
| `accountType` | `string` | yes |
| `active` | `boolean` | no |
| `notes` | `string` | no |

## UpdateActionRequest

| Field | Type | Required |
|---|---|---|
| `title` | `string` | no |
| `content` | `string` | no |
| `status` | `string` | no |
| `scheduledDate` | `string(date-time)` | no |
| `notificationMethod` | `string` | no |
| `assignedUserId` | `string(uuid)` | no |

## UpdateActorRequest

| Field | Type | Required |
|---|---|---|
| `firstName` | `string` | no |
| `lastName` | `string` | no |
| `phoneNumber` | `string` | no |
| `photoUri` | `string` | no |
| `photoId` | `string(uuid)` | no |

## UpdateAdministrativeGeneralOptionsRequest

| Field | Type | Required |
|---|---|---|
| `agencyId` | `string(uuid)` | no |
| `negotiateSellingPrice` | `boolean` | no |
| `sellingPriceIncludeVat` | `boolean` | no |
| `authorizeExceptionalDiscount` | `boolean` | no |
| `grantableDiscountRate` | `number(double)` | no |
| `printLogo` | `boolean` | no |
| `paperFormat` | `string` | no |
| `lengthOfVatInvoiceNumber` | `integer(int32)` | no |
| `prefixOfVatInvoiceNumber` | `string` | no |
| `lowStockAlert` | `boolean` | no |
| `preventiveMaintenanceAlert` | `boolean` | no |
| `defaultCurrency` | `string` | no |
| `legalIdentity` | `string` | no |
| `taxIdentifier` | `string` | no |
| `requireSalesOrderApproval` | `boolean` | no |
| `requireReturnApproval` | `boolean` | no |

## UpdateAdministrativePlatformOptionsRequest

| Field | Type | Required |
|---|---|---|
| `requireBusinessActorApproval` | `boolean` | no |
| `requireOrganizationApproval` | `boolean` | no |
| `allowOrganizationSelfServiceCreation` | `boolean` | no |
| `allowAgencySelfServiceCreation` | `boolean` | no |
| `allowRoleCloning` | `boolean` | no |
| `allowAgencyScopedCustomRoles` | `boolean` | no |
| `allowOrganizationAdminsToGovernAgencies` | `boolean` | no |
| `allowBusinessActorSelfReactivation` | `boolean` | no |

## UpdateAdministrativeRoleRequest

| Field | Type | Required |
|---|---|---|
| `name` | `string` | yes |

## UpdateAgencyRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `ownerId` | `string(uuid)` | no |
| `managerId` | `string(uuid)` | no |
| `name` | `string` | yes |
| `location` | `string` | no |
| `description` | `string` | no |
| `transferable` | `boolean` | no |
| `active` | `boolean` | no |
| `logoUri` | `string` | no |
| `logoId` | `string(uuid)` | no |
| `shortName` | `string` | no |
| `longName` | `string` | no |
| `isIndividualBusiness` | `boolean` | no |
| `isHeadquarter` | `boolean` | no |
| `country` | `string` | no |
| `city` | `string` | no |
| `latitude` | `number(double)` | no |
| `longitude` | `number(double)` | no |
| `openTime` | `string` | no |
| `closeTime` | `string` | no |
| `phone` | `string` | no |
| `email` | `string` | no |
| `whatsapp` | `string` | no |
| `greetingMessage` | `string` | no |
| `averageRevenue` | `number` | no |
| `capitalShare` | `number` | no |
| `registrationNumber` | `string` | no |
| `socialNetwork` | `string` | no |
| `taxNumber` | `string` | no |
| `keywords` | `string[]` | no |
| `isPublic` | `boolean` | no |
| `isBusiness` | `boolean` | no |
| `totalAffiliatedCustomers` | `integer(int32)` | no |
| `agencyType` | `string` | no |

## UpdateAppBusinessSettingsRequest

| Field | Type | Required |
|---|---|---|
| `agencyId` | `string(uuid)` | no |
| `negotiateSellingPrice` | `boolean` | no |
| `sellingPriceIncludeVat` | `boolean` | no |
| `authorizeExceptionalDiscount` | `boolean` | no |
| `grantableDiscountRate` | `number(double)` | no |
| `printLogo` | `boolean` | no |
| `paperFormat` | `string` | yes |
| `lengthOfVatInvoiceNumber` | `integer(int32)` | no |
| `prefixOfVatInvoiceNumber` | `string` | yes |
| `lowStockAlert` | `boolean` | no |
| `preventiveMaintenanceAlert` | `boolean` | no |
| `defaultCurrency` | `string` | no |
| `legalIdentity` | `string` | no |
| `taxIdentifier` | `string` | no |
| `requireSalesOrderApproval` | `boolean` | no |
| `requireReturnApproval` | `boolean` | no |

## UpdateArtistProfileRequest

| Field | Type | Required |
|---|---|---|
| `displayName` | `string` | no |
| `bannerFileId` | `string(uuid)` | no |
| `biography` | `string` | no |
| `location` | `string` | no |

## UpdateArtworkRequest

| Field | Type | Required |
|---|---|---|
| `title` | `string` | no |
| `description` | `string` | no |
| `technique` | `string` | no |
| `style` | `string` | no |
| `dimensions` | `string` | no |
| `tags` | `string[]` | no |

## UpdateAvatarRequest

| Field | Type | Required |
|---|---|---|
| `avatarId` | `string(uuid)` | no |

## UpdateBankRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `name` | `string` | yes |
| `active` | `boolean` | no |

## UpdateCashRegisterRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `label` | `string` | yes |
| `agencyId` | `string(uuid)` | no |
| `status` | `string` | yes |

## UpdateCashierProfileRequest

| Field | Type | Required |
|---|---|---|
| `kernelUserId` | `string(uuid)` | no |
| `email` | `string(email)` | no |
| `fullName` | `string` | yes |
| `agencyId` | `string(uuid)` | no |
| `kind` | `string` | yes |
| `active` | `boolean` | no |

## UpdateClientApplicationRequest

| Field | Type | Required |
|---|---|---|
| `name` | `string` | no |
| `description` | `string` | no |
| `planCode` | `string` | no |
| `allowedServices` | `string[]` | no |
| `requestQuotaLimit` | `integer(int64)` | no |
| `requestQuotaWindowSeconds` | `integer(int64)` | no |

## UpdateDepartmentRequest

| Field | Type | Required |
|---|---|---|
| `name` | `string` | no |
| `active` | `boolean` | no |

## UpdateEmergencyContactRequest

| Field | Type | Required |
|---|---|---|
| `nom` | `string` | no |
| `prenom` | `string` | no |
| `relation` | `string` | no |
| `telephone` | `string` | no |
| `email` | `string` | no |
| `priorite` | `integer(int32)` | no |

## UpdateEmployeeMembershipRequest

| Field | Type | Required |
|---|---|---|
| `roleId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `firstName` | `string` | no |
| `lastName` | `string` | no |
| `jobTitle` | `string` | no |
| `department` | `string` | no |
| `phoneNumber` | `string` | no |
| `employmentType` | `string` | no |
| `photoUri` | `string` | no |
| `photoId` | `string(uuid)` | no |

## UpdateEmployeeRequest

| Field | Type | Required |
|---|---|---|
| `numCnps` | `string` | no |
| `categorie` | `integer(int32)` | no |
| `echelon` | `string` | no |
| `departmentCode` | `string` | no |
| `modePaiement` | `string` | no |
| `compteBancaire` | `string` | no |
| `numMobileMoney` | `string` | no |
| `operateurMm` | `string` | no |
| `managerId` | `string(uuid)` | no |

## UpdateEntryRequest

| Field | Type | Required |
|---|---|---|
| `reference` | `string` | yes |
| `entryDate` | `string(date-time)` | yes |
| `lines` | `EntryLineRequest[]` | yes |

## UpdateIdentityOnboardingRequest

| Field | Type | Required |
|---|---|---|
| `accountType` | `string` | yes |
| `businessType` | `string` | no |
| `step` | `integer(int32)` | no |
| `status` | `string` | no |
| `data` | `object` | no |

## UpdateJournalRequest

| Field | Type | Required |
|---|---|---|
| `label` | `string` | yes |
| `type` | `string` | yes |
| `active` | `boolean` | no |

## UpdateMyProfileRequest

| Field | Type | Required |
|---|---|---|
| `displayName` | `string` | yes |
| `email` | `string(email)` | no |

## UpdateOnboardingRequest

| Field | Type | Required |
|---|---|---|
| `step` | `integer(int32)` | no |
| `status` | `string` | no |

## UpdateOrganizationRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `service` | `string` | yes |
| `isIndividualBusiness` | `boolean` | no |
| `email` | `string(email)` | no |
| `shortName` | `string` | yes |
| `longName` | `string` | yes |
| `description` | `string` | no |
| `logoUri` | `string` | no |
| `logoId` | `string(uuid)` | no |
| `websiteUrl` | `string` | no |
| `socialNetwork` | `string` | no |
| `businessRegistrationNumber` | `string` | no |
| `taxNumber` | `string` | no |
| `capitalShare` | `number` | no |
| `ceoName` | `string` | no |
| `yearFounded` | `integer(int32)` | no |
| `keywords` | `string[]` | no |
| `numberOfEmployees` | `integer(int32)` | no |
| `legalForm` | `string` | no |
| `isActive` | `boolean` | no |
| `status` | `string` | no |

## UpdateOrganizationServiceQuotaRequest

| Field | Type | Required |
|---|---|---|
| `requestQuotaLimit` | `integer(int64)` | yes |
| `requestQuotaWindowSeconds` | `integer(int64)` | yes |

## UpdatePaymentRequest

| Field | Type | Required |
|---|---|---|
| `billingDocumentId` | `string(uuid)` | no |
| `invoiceId` | `string(uuid)` | no |
| `supplierInvoiceId` | `string(uuid)` | no |
| `counterpartyThirdPartyId` | `string(uuid)` | no |
| `reference` | `string` | yes |
| `amount` | `number` | yes |
| `currency` | `string` | yes |
| `status` | `string` | no |
| `paidAt` | `string(date-time)` | no |

## UpdatePlanRequest

| Field | Type | Required |
|---|---|---|
| `plan` | `string` | yes |

## UpdateProductCategoryRequest

| Field | Type | Required |
|---|---|---|
| `name` | `string` | yes |
| `parentCode` | `string` | no |
| `description` | `string` | no |

## UpdateProductRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `sku` | `string` | yes |
| `name` | `string` | yes |
| `familyCode` | `string` | yes |
| `categoryCode` | `string` | no |
| `variantLabel` | `string` | yes |
| `barcode` | `string` | no |
| `description` | `string` | no |
| `minStockLevel` | `integer(int32)` | no |
| `maxStockLevel` | `integer(int32)` | no |
| `unitPrice` | `number` | yes |
| `currency` | `string` | yes |
| `status` | `string` | yes |
| `cost` | `number` | no |
| `photo` | `string` | no |
| `uom` | `string` | no |
| `quantity` | `number` | no |
| `allowedSaleSizes` | `SaleSize[]` | no |

## UpdateStatusRequest

| Field | Type | Required |
|---|---|---|
| `status` | `string` | yes |

## UpdateThirdPartyRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `name` | `string` | yes |
| `roles` | `string[]` | yes |
| `accountingAccount` | `string` | no |
| `segment` | `string` | no |
| `qualificationScore` | `integer(int32)` | no |
| `enabled` | `boolean` | no |
| `prospect` | `boolean` | no |
| `type` | `string` | no |
| `legalForm` | `string` | no |
| `uniqueIdentificationNumber` | `string` | no |
| `tradeRegistrationNumber` | `string` | no |
| `acronym` | `string` | no |
| `longName` | `string` | no |
| `logoUri` | `string` | no |
| `logoId` | `string(uuid)` | no |
| `accountingAccountNumbers` | `string[]` | no |
| `authorizedPaymentMethods` | `string[]` | no |
| `authorizedCreditLimit` | `number` | no |
| `maxDiscountRate` | `number` | no |
| `vatSubject` | `boolean` | no |
| `operationsBalance` | `number` | no |
| `openingBalance` | `number` | no |
| `payTermNumber` | `integer(int32)` | no |
| `payTermType` | `string` | no |
| `thirdPartyFamily` | `string` | no |
| `classification` | `string` | no |
| `taxNumber` | `string` | no |

## UpdateTransactionTypeRequest

| Field | Type | Required |
|---|---|---|
| `label` | `string` | yes |
| `inbound` | `boolean` | no |
| `category` | `string` | no |
| `description` | `string` | no |

## UpdateWarehouseRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `name` | `string` | yes |

## UpsertAssetProfileRequest

| Field | Type | Required |
|---|---|---|
| `physicalSpaceId` | `string(uuid)` | no |
| `ownerActorId` | `string(uuid)` | no |
| `supplierThirdPartyId` | `string(uuid)` | no |
| `assetClass` | `string` | yes |
| `criticality` | `string` | yes |
| `lifecyclePhase` | `string` | yes |
| `complianceStatus` | `string` | yes |
| `acquisitionCost` | `number` | no |
| `currentValue` | `number` | no |
| `depreciationMethod` | `string` | yes |
| `acquisitionDate` | `string(date-time)` | no |
| `warrantyUntil` | `string(date-time)` | no |
| `expectedRenewalDate` | `string(date-time)` | no |
| `lastComplianceCheckAt` | `string(date-time)` | no |
| `nextComplianceCheckAt` | `string(date-time)` | no |
| `maintenanceContractReference` | `string` | no |
| `notes` | `string` | no |

## UpsertCategoryTranslationRequest

| Field | Type | Required |
|---|---|---|
| `locale` | `string` | yes |
| `name` | `string` | yes |
| `description` | `string` | no |

## UpsertDocumentSequenceRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `documentType` | `string` | yes |
| `prefix` | `string` | no |
| `suffix` | `string` | no |
| `paddingWidth` | `integer(int32)` | no |
| `nextNumber` | `integer(int64)` | no |

## UpsertOpeningHoursRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | yes |
| `agencyId` | `string(uuid)` | yes |
| `dayOfWeek` | `string` | yes |
| `opensAt` | `string` | no |
| `closesAt` | `string` | no |
| `closed` | `boolean` | no |

## UpsertOperationalPolicyRequest

| Field | Type | Required |
|---|---|---|
| `assignmentRequiresApproval` | `boolean` | no |
| `allowCrossAgencyAssetAssignment` | `boolean` | no |
| `siteOpeningChecklistRequired` | `boolean` | no |
| `mandatoryDocumentApproval` | `boolean` | no |
| `inventoryVarianceTolerancePercent` | `integer(int32)` | no |
| `maintenanceAlertThresholdDays` | `integer(int32)` | no |
| `lowUtilizationThresholdPercent` | `integer(int32)` | no |
| `maxOpenInventoryCampaigns` | `integer(int32)` | no |
| `requireInventorySupervisorApproval` | `boolean` | no |
| `automaticLifecycleEvents` | `boolean` | no |
| `strictDocumentExpiry` | `boolean` | no |

## UpsertOperationalSiteProfileRequest

| Field | Type | Required |
|---|---|---|
| `siteCategory` | `string` | yes |
| `operatingModel` | `string` | yes |
| `openingStatus` | `string` | yes |
| `cashEnabled` | `boolean` | no |
| `warehouseEnabled` | `boolean` | no |
| `maintenanceEnabled` | `boolean` | no |
| `inventoryEnabled` | `boolean` | no |
| `documentComplianceRequired` | `boolean` | no |
| `defaultPhysicalSpaceId` | `string(uuid)` | no |
| `readinessNotes` | `string` | no |
| `commissionedAt` | `string(date-time)` | no |

## UpsertPayrollEmployeeRequest

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `agencyId` | `string(uuid)` | no |
| `matricule` | `string` | no |
| `displayName` | `string` | no |
| `email` | `string` | no |
| `socialSecurityNo` | `string` | no |
| `categorie` | `integer(int32)` | no |
| `echelon` | `string` | no |
| `departmentCode` | `string` | no |
| `hireDate` | `string(date)` | no |
| `maritalStatus` | `string` | no |
| `dependentChildren` | `integer(int32)` | no |
| `baseSalary` | `number` | no |
| `benefitsInKind` | `number` | no |
| `position` | `string` | no |
| `paymentChannel` | `string` | no |
| `accountRef` | `string` | no |

## UpsertPersonalInfoRequest

| Field | Type | Required |
|---|---|---|
| `lieuNaissance` | `string` | no |
| `situationMatrimoniale` | `string` | no |
| `typePiece` | `string` | no |
| `numeroPiece` | `string` | no |
| `dateEmissionPiece` | `string(date)` | no |
| `niuFiscal` | `string` | no |
| `permisConduire` | `string` | no |
| `languesParlees` | `string` | no |
| `emailPersonnel` | `string` | no |
| `telephoneDomicile` | `string` | no |
| `whatsapp` | `string` | no |
| `adressePostale` | `string` | no |
| `adresseDomicile` | `string` | no |
| `ville` | `string` | no |
| `region` | `string` | no |
| `codePostal` | `string` | no |

## UpsertPolicyRequest

| Field | Type | Required |
|---|---|---|
| `mandatory` | `boolean` | no |
| `approvalRequired` | `boolean` | no |
| `expiryDays` | `integer(int32)` | no |
| `reviewerResponsibilityType` | `string` | no |

## UpsertProductLocationRequest

| Field | Type | Required |
|---|---|---|
| `binCode` | `string` | yes |
| `quantity` | `number` | no |
| `note` | `string` | no |

## UpsertProductSpecRequest

| Field | Type | Required |
|---|---|---|
| `weightKg` | `number` | no |
| `lengthCm` | `number` | no |
| `widthCm` | `number` | no |
| `heightCm` | `number` | no |
| `materials` | `string` | no |

## UpsertSettingRequest

| Field | Type | Required |
|---|---|---|
| `code` | `string` | yes |
| `value` | `string` | yes |

## UserAccountResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `actorId` | `string(uuid)` | no |
| `username` | `string` | no |
| `email` | `string` | no |
| `phoneNumber` | `string` | no |
| `authProvider` | `string` | no |
| `externalSubject` | `string` | no |
| `status` | `string` | no |
| `plan` | `string` | no |
| `onboardingStatus` | `string` | no |
| `onboardingStep` | `integer(int32)` | no |
| `accountType` | `string` | no |
| `businessType` | `string` | no |
| `onboardingPayload` | `string` | no |
| `emailVerified` | `boolean` | no |
| `emailVerifiedAt` | `string(date-time)` | no |
| `phoneVerified` | `boolean` | no |
| `phoneVerifiedAt` | `string(date-time)` | no |
| `mfaEnabled` | `boolean` | no |
| `mfaChannel` | `string` | no |
| `avatarId` | `string(uuid)` | no |
| `organizations` | `UserOrganizationAccessResponse[]` | no |

## UserOrganizationAccessResponse

| Field | Type | Required |
|---|---|---|
| `organizationId` | `string(uuid)` | no |
| `organizationCode` | `string` | no |
| `shortName` | `string` | no |
| `longName` | `string` | no |
| `displayName` | `string` | no |
| `legalName` | `string` | no |
| `services` | `string[]` | no |

## UserRoleAssignmentResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `userId` | `string(uuid)` | no |
| `roleId` | `string(uuid)` | no |
| `scopeType` | `string` | no |
| `scopeId` | `string(uuid)` | no |
| `scope` | `string` | no |

## ValidateGalleryTicketRequest

| Field | Type | Required |
|---|---|---|
| `qrCodeData` | `string` | yes |

## VariantAttributeResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `variantId` | `string(uuid)` | no |
| `attributeName` | `string` | no |
| `attributeValue` | `string` | no |

## VariantPriceResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `variantId` | `string(uuid)` | no |
| `priceType` | `string` | no |
| `amount` | `number` | no |
| `currency` | `string` | no |
| `effectiveFrom` | `string(date-time)` | no |

## VariantResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `productId` | `string(uuid)` | no |
| `sku` | `string` | no |
| `barcode` | `string` | no |
| `label` | `string` | no |
| `isDefault` | `boolean` | no |
| `status` | `string` | no |

## VerifyCaptchaRequest

| Field | Type | Required |
|---|---|---|
| `captchaToken` | `string` | yes |
| `answer` | `string` | yes |

## VerifyOtpRequest

| Field | Type | Required |
|---|---|---|
| `challengeToken` | `string` | yes |
| `code` | `string` | yes |
| `purpose` | `string` | no |

## WalletAccountView

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `ownerId` | `string(uuid)` | no |
| `ownerName` | `string` | no |
| `number` | `string` | no |
| `balance` | `number` | no |
| `currency` | `string` | no |
| `type` | `string` | no |
| `linkedThirdPartyId` | `string(uuid)` | no |

## WalletResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `ownerId` | `string(uuid)` | no |
| `ownerName` | `string` | no |
| `balance` | `number` | no |
| `createdAt` | `string(date-time)` | no |
| `updatedAt` | `string(date-time)` | no |

## WarehouseLayoutRequest

| Field | Type | Required |
|---|---|---|
| `agencyId` | `string(uuid)` | no |
| `type` | `string` | yes |
| `width` | `integer(int32)` | no |
| `height` | `integer(int32)` | no |
| `layout` | `JsonNode` | no |

## WarehouseLayoutResponse

| Field | Type | Required |
|---|---|---|
| `agencyId` | `string(uuid)` | no |
| `type` | `string` | no |
| `width` | `integer(int32)` | no |
| `height` | `integer(int32)` | no |
| `layout` | `JsonNode` | no |

## WarehouseScopedResourceRequest

| Field | Type | Required |
|---|---|---|
| `resourceCode` | `string` | yes |
| `name` | `string` | yes |
| `category` | `string` | yes |
| `serialNumber` | `string` | yes |
| `latitude` | `number(double)` | no |
| `longitude` | `number(double)` | no |
| `ipAddress` | `string` | no |
| `macAddress` | `string` | no |

## WarehouseTransferResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `tenantId` | `string(uuid)` | no |
| `organizationId` | `string(uuid)` | no |
| `sourceAgencyId` | `string(uuid)` | no |
| `targetAgencyId` | `string(uuid)` | no |
| `productId` | `string(uuid)` | no |
| `referenceNumber` | `string` | no |
| `quantity` | `number` | no |
| `status` | `string` | no |

## WithdrawRequest

| Field | Type | Required |
|---|---|---|
| `accountId` | `string(uuid)` | yes |
| `amount` | `number` | yes |
| `reference` | `string` | yes |
| `sessionId` | `string(uuid)` | no |
| `registerId` | `string(uuid)` | no |
| `counterpartyActorId` | `string(uuid)` | no |
| `counterpartyThirdPartyId` | `string(uuid)` | no |

## WorkflowRequestResponse

| Field | Type | Required |
|---|---|---|
| `id` | `string(uuid)` | no |
| `type` | `string` | no |
| `status` | `string` | no |
| `requestedBy` | `string` | no |
| `approvedBy` | `string` | no |
| `approvedAt` | `string(date-time)` | no |
| `payloadJson` | `string` | no |
| `updatedAt` | `string(date-time)` | no |

## WorkspaceAssetSummary

| Field | Type | Required |
|---|---|---|
| `totalResources` | `integer(int64)` | no |
| `assignedResources` | `integer(int64)` | no |
| `reservedResources` | `integer(int64)` | no |
| `openMaintenanceCount` | `integer(int64)` | no |

## WorkspaceDocumentSummary

| Field | Type | Required |
|---|---|---|
| `totalDocuments` | `integer(int32)` | no |
| `countsByTargetType` | `object` | no |
| `countsByCategory` | `object` | no |

## WorkspaceInventorySummary

| Field | Type | Required |
|---|---|---|
| `catalogProductCount` | `integer(int32)` | no |
| `activeCatalogProductCount` | `integer(int32)` | no |
| `scopedProductCount` | `integer(int32)` | no |
| `validatedStockMovementCount` | `integer(int32)` | no |
| `inventorySessionCount` | `integer(int32)` | no |
| `warehouseTransferCount` | `integer(int32)` | no |

## WorkspacePhysicalLayoutSummary

| Field | Type | Required |
|---|---|---|
| `totalSpaces` | `integer(int32)` | no |
| `activeSpaces` | `integer(int32)` | no |
| `rootSpaceCount` | `integer(int32)` | no |

## WorkspaceReadiness

| Field | Type | Required |
|---|---|---|
| `organizationReady` | `boolean` | no |
| `servicesReady` | `boolean` | no |
| `agenciesReady` | `boolean` | no |
| `warehousesReady` | `boolean` | no |
| `productsReady` | `boolean` | no |
| `assetsReady` | `boolean` | no |
| `physicalLayoutReady` | `boolean` | no |
| `documentsReady` | `boolean` | no |
| `ready` | `boolean` | no |
| `missingCapabilities` | `string[]` | no |
