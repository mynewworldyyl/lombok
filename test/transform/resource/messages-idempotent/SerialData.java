
import java.util.Date;
import java.io.File;
import cn.jmicro.api.codec.ISerializeObject;

@lombok.Serial public class SerialData extends SuperSerialDataJRso implements ISerializeObject{
  //private int x;
 
  private Integer integerVal;
  private Byte byteVal;
  private java.lang.String strVal;
  
  private Date dateVal;
  
  private SuperSerialDataJRso fvo;
  
}
