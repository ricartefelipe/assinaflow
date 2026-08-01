package br.com.ricarte.assinaflow.totalrecall.dto;

import br.com.ricarte.assinaflow.user.UserRole;

public record TotalRecallProvisionResponse(
        boolean ok,
        String email,
        UserRole role,
        boolean enabled
) {
}
