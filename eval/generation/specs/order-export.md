# Order export

Customers can export their paid orders as a CSV file from their account page.

## Requirements
- The export contains one row per order: order number, date, total amount including VAT.
- Cancelled orders are excluded.
- The file name is `orders-YYYY-MM-DD.csv`.

## Open question
The delimiter (comma or semicolon) and the encoding of the file are not decided yet.
