-- Create the second database
CREATE DATABASE IF NOT EXISTS catalog_db;

-- Switch to it
USE catalog_db;

-- Create the table (Matching your Product Catalog Entity)
CREATE TABLE IF NOT EXISTS product_offerings (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price DECIMAL(10, 2) NOT NULL
);

-- Seed the required data
INSERT INTO product_offerings (id, name, price) VALUES ('po-1', 'Small Widget', 29.99);
INSERT INTO product_offerings (id, name, price) VALUES ('po-2', 'Big Widget', 49.99);
INSERT INTO product_offerings (id, name, price) VALUES ('po-3', 'Mega Widget', 199.99);
