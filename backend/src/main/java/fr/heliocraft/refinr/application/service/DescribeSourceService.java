// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.DescribeSource;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;

public class DescribeSourceService implements DescribeSource {

    private final DocumentSourcePort documentSource;
    private final int maxContentChars;

    public DescribeSourceService(DocumentSourcePort documentSource, int maxContentChars) {
        this.documentSource = documentSource;
        this.maxContentChars = DocumentAnalysis.requirePositiveLimit(maxContentChars);
    }

    @Override
    public String displayName() {
        return documentSource.displayName();
    }

    @Override
    public boolean acceptsSpace(String space) {
        return space != null && !space.isBlank() && documentSource.acceptsSpace(space);
    }

    @Override
    public String documentUrl(String documentId) {
        return documentId != null && documentSource.acceptsDocumentId(documentId)
                ? documentSource.documentUrl(documentId) : null;
    }

    @Override
    public int maxContentChars() {
        return maxContentChars;
    }
}
