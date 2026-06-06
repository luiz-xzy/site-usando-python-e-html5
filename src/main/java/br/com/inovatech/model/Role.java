package br.com.inovatech.model;

/**
 * Perfis de acesso dos usuários do sistema.
 */
public enum Role {
    GERENTE("Gerente", true, true, true),
    SUPERVISOR("Supervisor", true, true, false);

    private final String descricao;
    private final boolean podeVisualizarRelatorios;
    private final boolean podeRegistrarMovimentos;
    private final boolean podeGerenciarUsuarios;

    Role(String descricao,
         boolean podeVisualizarRelatorios,
         boolean podeRegistrarMovimentos,
         boolean podeGerenciarUsuarios) {
        this.descricao = descricao;
        this.podeVisualizarRelatorios = podeVisualizarRelatorios;
        this.podeRegistrarMovimentos = podeRegistrarMovimentos;
        this.podeGerenciarUsuarios = podeGerenciarUsuarios;
    }

    public String getDescricao() { return descricao; }
    public boolean isPodeVisualizarRelatorios() { return podeVisualizarRelatorios; }
    public boolean isPodeRegistrarMovimentos() { return podeRegistrarMovimentos; }
    public boolean isPodeGerenciarUsuarios() { return podeGerenciarUsuarios; }

    @Override
    public String toString() {
        return descricao;
    }
}
