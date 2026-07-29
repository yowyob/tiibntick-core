<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
            http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.8.xsd">

    <changeSet id="20260727-create-pricing-catalog" author="francois-charles">
        <comment>Create pricing_catalog table for subscription prices (XAF)</comment>
        <createTable tableName="pricing_catalog">
            <column name="id" type="UUID">
                <constraints primaryKey="true" nullable="false"/>
                <defaultValueComputed>uuid_generate_v4()</defaultValueComputed>
            </column>
            <column name="subscription_type" type="VARCHAR(50)">
                <constraints nullable="false" unique="true"/>
            </column>
            <column name="price_xaf" type="NUMERIC">
                <constraints nullable="false"/>
            </column>
        </createTable>

        <insert tableName="pricing_catalog">
            <column name="subscription_type" value="FREE"/>
            <column name="price_xaf" valueNumeric="0"/>
        </insert>
        <insert tableName="pricing_catalog">
            <column name="subscription_type" value="BASIC"/>
            <column name="price_xaf" valueNumeric="7500"/>
        </insert>
        <insert tableName="pricing_catalog">
            <column name="subscription_type" value="STANDARD"/>
            <column name="price_xaf" valueNumeric="10000"/>
        </insert>
        <insert tableName="pricing_catalog">
            <column name="subscription_type" value="PREMIUM"/>
            <column name="price_xaf" valueNumeric="10000"/>
        </insert>
        <insert tableName="pricing_catalog">
            <column name="subscription_type" value="ADVANCE"/>
            <column name="price_xaf" valueNumeric="15000"/>
        </insert>
    </changeSet>
</databaseChangeLog>
