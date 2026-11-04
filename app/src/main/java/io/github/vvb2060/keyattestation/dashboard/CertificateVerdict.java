package io.github.vvb2060.keyattestation.dashboard;
/** A certificate result, not a claim that the entire phone is safe. */
public record CertificateVerdict(int level,String title,String reason) {
    public static final int NORMAL=0, WARNING=1, ERROR=2;
    public static CertificateVerdict evaluate(boolean signature,boolean trustedRoot,boolean revoked,
            boolean validDates,boolean compatibility,boolean fresh,boolean deviceWarning,boolean lost) {
        if(!signature)return new CertificateVerdict(ERROR,"证书异常","签名或证书连接校验失败");
        if(revoked)return new CertificateVerdict(ERROR,"证书异常","命中撤销或暂停列表");
        if(!validDates)return new CertificateVerdict(ERROR,"证书异常","证书已过期或尚未生效");
        if(!trustedRoot)return new CertificateVerdict(WARNING,"待确认","证书链完整，但信任根未识别");
        if(!fresh)return new CertificateVerdict(WARNING,"待确认","撤销数据已过期或尚未取得更新");
        if(compatibility)return new CertificateVerdict(WARNING,"兼容通过","已应用旧工厂证书过期兼容策略");
        if(lost)return new CertificateVerdict(WARNING,"待确认","配置证书携带遗失设备标记");
        if(deviceWarning)return new CertificateVerdict(WARNING,"证书链通过","设备启动状态或硬件证明需要检查");
        return new CertificateVerdict(NORMAL,"证书正常","信任根、签名、有效期及撤销检查通过");
    }
}
