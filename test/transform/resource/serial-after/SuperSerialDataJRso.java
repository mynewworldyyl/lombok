import java.util.Date;
import java.io.File;
public @lombok.Serial class SuperSerialDataJRso implements cn.jmicro.api.codec.ISerializeObject {
  private int x;
  public SuperSerialDataJRso() {
    super();
  }
  public @java.lang.SuppressWarnings("all") void encode(java.io.DataOutput buf) throws java.io.IOException {
    final cn.jmicro.api.codec.JDataOutput out = (cn.jmicro.api.codec.JDataOutput) buf;
    out.writeInt(this.x);
  }
  public @java.lang.SuppressWarnings("all") void decode(java.io.DataOutput buf) throws java.io.IOException {
    final cn.jmicro.api.codec.JDataInput in = (cn.jmicro.api.codec.JDataInput) buf;
    this.x = in.readInt();
  }
}