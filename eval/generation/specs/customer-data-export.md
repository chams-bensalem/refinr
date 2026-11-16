# Customer data export

Support agents need to export the data of a customer account.

## Requirements
- One user story: as a support agent, I want to export the data of a customer account, so that I can answer customer requests.

## Open points (not decided)
- **Format**: not decided. CSV, JSON or a ZIP archive with several files? Nobody has chosen yet.
- **Encoding and delimiter**: unknown. UTF-8 or Windows-1252? Comma or semicolon? To be decided by the data team.
- **Access rights**: unknown. Which roles may export (support level 1, level 2, admins only)? Legal has not answered.
- **Content**: unknown. Which fields are included (orders, invoices, personal data, support history)? GDPR review pending.
- **Size limits and delivery**: unknown. Download in the browser or by email? What happens for very large accounts?

There are no acceptance criteria yet: every point above must be decided before development can start.
