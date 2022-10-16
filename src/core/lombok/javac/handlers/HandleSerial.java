/*
 * Copyright (C) 2009-2021 The Project Lombok Authors.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package lombok.javac.handlers;

import static lombok.javac.Javac.*;
import static lombok.javac.handlers.JavacHandlerUtil.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;

import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCArrayTypeTree;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCExpressionStatement;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCPrimitiveTypeTree;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCTypeCast;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import lombok.Serial;
import lombok.core.AST.Kind;
import lombok.core.AnnotationValues;
import lombok.core.configuration.CheckerFrameworkVersion;
import lombok.javac.Javac;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import lombok.spi.Provides;

/**
 * Handles the {@code ToString} annotation for javac.
 */
@Provides
public class HandleSerial extends JavacAnnotationHandler<Serial> {
	@Override public void handle(AnnotationValues<Serial> annotation, JCAnnotation ast, JavacNode annotationNode) {
		// handleFlagUsage(annotationNode,
		// ConfigurationKeys.TO_STRING_FLAG_USAGE, "@ToString");
		
		// deleteAnnotationIfNeccessary(annotationNode, Serial.class);
		
		JavacNode classNode = annotationNode.up();
		java.util.List<JavacNode> members = new ArrayList<JavacNode>();
		
		for(JavacNode child : classNode.down()) {
			if (child.getKind() == Kind.FIELD && !child.isStatic() && !child.isTransient()) {
				members.add(child);
			}
		}
		
		if (members.size() > 1) {
			members.sort(new Comparator<JavacNode>() {
				@Override public int compare(JavacNode s1, JavacNode s2) {
					return s1.getName().compareTo(s2.getName());
				}
			});
			
			/*
			 * Collections.sort(members, new Comparator<JavacNode>() {
			 * 
			 * @Override public int compare(JavacNode s1, JavacNode s2) { return
			 * s1.getName().compareTo(s2.getName()); } });
			 */
		}
		
		if(!isClassOrEnum(classNode)) {
			classNode.addError("@Serial is only supported on a class or enum.");
			return;
		}
		
		JCClassDecl jcClassDecl = (JCClassDecl)classNode.get();
		JCExpression serializeObjectType = memberTypeExpression(classNode, "cn.jmicro.api.codec.ISerializeObject");
		jcClassDecl.implementing = jcClassDecl.implementing.append(serializeObjectType);
		
		switch (methodExists("encode", classNode, 1)) {
		case NOT_EXISTS:
			createEncodeMethod0(members, annotationNode.up(), annotationNode);
			break;
		case EXISTS_BY_LOMBOK:
			break;
		default:
		case EXISTS_BY_USER:
			annotationNode.up().addWarning("Not generating encode(): A method with that name already exists");
			break;
		}
		
		switch (methodExists("decode", classNode, 1)) {
		case NOT_EXISTS:
			createDecodeMethod0(members, annotationNode.up(), annotationNode);
			break;
		case EXISTS_BY_LOMBOK:
			break;
		default:
		case EXISTS_BY_USER:
			annotationNode.up().addWarning("Not generating decode(): A method with that name already exists");
			break;
		}
		
		System.out.println(classNode.toString());
	}
	
	private void createDecodeMethod0(Collection<JavacNode> members, JavacNode typeNode, JavacNode source) {
		
		JavacTreeMaker maker = typeNode.getTreeMaker();
		
		String methodName = "decode";
		
		List<JCStatement> sts = List.<JCStatement>nil();
		
		JCExpression paramType = chainDots(typeNode, "java", "io", "DataInput");
		JCExpression bufferType = memberTypeExpression(typeNode, "cn.jmicro.api.codec.JDataInput");
		JCExpression typeCoderType = memberTypeExpression(typeNode, "cn.jmicro.api.codec.typecoder.ITypeCoder");
		JCExpression serializeObjectType = memberTypeExpression(typeNode, "cn.jmicro.api.codec.ISerializeObject");
		
		//cn.jmicro.api.codec.JDataInput in =
		//(cn.jmicro.api.codec.JDataInput)buf;\n
		//JCVariableDecl typeCoderVar = maker.VarDef(maker.Modifiers(0), typeNode.toName("__coder"), typeCoderType, null);
		//sts = sts.append(typeCoderVar);
		
		Name bufName = typeNode.toName("buf");
		JCIdent bufIntent = maker.Ident(bufName);
		
		Name decodeName = typeNode.toName(methodName);
		
		long flags = JavacHandlerUtil.addFinalIfNeeded(Flags.PARAMETER, typeNode.getContext());
		JCVariableDecl var = maker.VarDef(maker.Modifiers(flags), bufName, paramType, null);
		
		JCTypeCast jdEXp = maker.TypeCast(bufferType, bufIntent);
		JCVariableDecl inVar = maker.VarDef(maker.Modifiers(0), typeNode.toName("in"), bufferType, jdEXp);
		sts = sts.append(inVar);
		
		if(!isDirectDescendantOfObject(typeNode)) {
			//super.decode(buf);
			JCTree extending = Javac.getExtendsClause((JCClassDecl) typeNode.get());
			if (extending instanceof JCIdent && 
				((JCIdent)extending).sym.getAnnotation(Serial.class) != null) {

				JCStatement callToSuper = maker.Exec(maker.Apply(List.<JCExpression>nil(), 
					maker.Select(maker.Ident(typeNode.toName("super")), typeNode.toName(methodName)), 
					List.<JCExpression>of(bufIntent)));
				sts = sts.append(callToSuper);
			}
		}
		
		for (JavacNode f : members) {
			JCVariableDecl fvarDecl = (JCVariableDecl) f.get();
			
			JCExpression val = maker.Select(maker.Ident(typeNode.toName("this")), typeNode.toName(f.getName()));
			
			JCStatement st = null;
			String readMethodName = null;
			
			int i = 0;
			String tname = fvarDecl.getType().type.toString();
			
			if (f.isPrimitive()) {
				switch (((JCPrimitiveTypeTree) fvarDecl.getType()).getPrimitiveTypeKind()) {
				case BOOLEAN:
					readMethodName = "readBoolean";
					break;
				case CHAR:
					readMethodName = "readChar";
					break;
				case BYTE:
					readMethodName = "readByte";
					break;
				case SHORT:
					readMethodName = "readShort";
					break;
				case INT:
					readMethodName = "readInt";
					break;
				case LONG:
					readMethodName = "readLong";
					break;
				case FLOAT:
					readMethodName = "readFloat";
					break;
				case DOUBLE:
					readMethodName = "readDouble";
					break;
				default:
					throw new RuntimeException("Not support primitive: " + f.toString());
				}
				sts = readVal(typeNode, maker, readMethodName, val, sts);
			} else if (tname.equals(Integer.class.getName())) {
				sts = readVal(typeNode, maker, "readInt", val, sts);
			} else if (tname.equals(Long.class.getName())) {
				sts = readVal(typeNode, maker, "readLong", val, sts);
			} else if (tname.equals(String.class.getName())) {
				sts = readVal(typeNode, maker, "readUTF", val, sts);
			} else if (tname.equals(Byte.class.getName())) {
				sts = readVal(typeNode, maker, "readByte", val, sts);
			} else if (tname.equals(Double.class.getName())) {
				sts = readVal(typeNode, maker, "readDouble", val,  sts);
			} else if (tname.equals(Float.class.getName())) {
				sts = readVal(typeNode, maker, "readFloat", val,  sts);
			} else if (tname.equals(Boolean.class.getName())) {
				sts = readVal(typeNode, maker, "readBoolean", val, sts);
			} else if (tname.equals(Character.class.getName())) {
				sts = readVal(typeNode, maker, "readChar", val, sts);
			} else if (tname.equals(Short.class.getName())) {
				sts = readVal(typeNode, maker, "readShort", val,sts);
			} else if (tname.equals(Date.class.getName())) {
				//this.data = new Date();
				JCExpression dateType = chainDots(typeNode, "java", "util", "Date");
				JCNewClass newDate = maker.NewClass(null, List.<JCExpression>nil(), dateType, List.<JCExpression>nil(), null);
				sts = sts.append(maker.Exec(maker.Assign(val, newDate)));
				
				//readLong()
				JCMethodInvocation rexp = maker.Apply(List.<JCExpression>nil(), 
					maker.Select(maker.Ident(typeNode.toName("in")),typeNode.toName("readLong")), List.<JCExpression>nil());
				
				//this.data.setTime(in.readLong());
				JCExpression curTimeExp = maker.Apply(List.<JCExpression>nil(), 
					maker.Select(val, typeNode.toName("setTime")), List.<JCExpression>of(rexp));
				
				sts = sts.append(maker.Exec(curTimeExp));
			} else {
				
				/*
				 sb.append(" if(in.readByte() == cn.jmicro.api.codec.DecoderConstant.PREFIX_TYPE_NULL) { "+varName+"=null; } else { \n");
				
				if(isSOClass(fieldDeclareType.getName())) {
					sb.append(varName).append(" = new ").append(f.getType().getName()).append("();\n");
					sb.append(" ((cn.jmicro.api.codec.ISerializeObject)"+varName+").decode(__buffer);\n }");
				} else {
					sb.append(varName).append(" = (")
					.append(fieldDeclareType.getName()).append(") __coder.decode(__buffer,")
					.append(fieldDeclareType.getName()).append(".class,").append(" null );\n }");
				}
				 */
				
				JCExpression nullPrefix = chainDots(typeNode, "cn", "jmicro", "api", "codec", "DecoderConstant", "PREFIX_TYPE_NULL");
				//JCExpression proxyPrefix = chainDots(typeNode, "cn", "jmicro", "api", "codec", "DecoderConstant", "PREFIX_TYPE_PROXY");
				
				//in.readByte()
				JCMethodInvocation readByteExp = maker.Apply(List.<JCExpression>nil(), 
					maker.Select(maker.Ident(typeNode.toName("in")), typeNode.toName("readByte")),
					List.<JCExpression>nil());
				
				//判断是否为空值  in.readByte() == cn.jmicro.api.codec.DecoderConstant.PREFIX_TYPE_NULL
				JCExpression isNullExp = maker.Binary(CTC_EQUAL, readByteExp, nullPrefix);
				
				//设置空值"+varName+"=null;
				JCExpressionStatement setNullVal = maker.Exec(maker.Assign(val, maker.Literal(CTC_BOT, null)));
				
				//非空值
				List<JCStatement> elseStatements = List.<JCStatement>nil();
				
				JCExpression jcftype = (JCExpression)fvarDecl.getType();
				if (jcftype instanceof JCIdent && 
					((JCIdent)jcftype).sym.getAnnotation(Serial.class) != null) {
					//sb.append(varName).append(" = new ").append(f.getType().getName()).append("();\n");
					JCExpression valType =JavacHandlerUtil.cloneType(maker,jcftype,typeNode);
					JCNewClass newVal = maker.NewClass(null, List.<JCExpression>nil(), valType, List.<JCExpression>nil(), null);
					elseStatements = elseStatements.append(maker.Exec(maker.Assign(val, newVal)));
					
					//sb.append(" ((cn.jmicro.api.codec.ISerializeObject)"+varName+").decode(__buffer);\n }");
					JCTypeCast tc = maker.TypeCast(serializeObjectType,val);
					
					// in.decode();\n
					st = maker.Exec(maker.Apply(List.<JCExpression>nil(/*paramType*/), maker.Select(tc, decodeName), List.<JCExpression>of(bufIntent)));
					elseStatements = elseStatements.append(st);
					
				} else {
					
					//JCExpression psTypeExp = memberTypeExpression(typeNode, tname + ".class");
					//JCExpression gsTypeExp = maker.Literal(CTC_BOT, null);
					//JCExpression classType = chainDots(typeNode, "java", "lang", "Class");
					JCExpression psTypeExp = null;
					JCExpression gsTypeExp = maker.Literal(CTC_BOT, null);
					
					if(fvarDecl.getType() instanceof ParameterizedTypeTree) {
						ParameterizedTypeTree pjcType = (ParameterizedTypeTree)fvarDecl.getType();
						psTypeExp = memberTypeExpression(typeNode, pjcType.getType().toString() + ".class");
					}else if(fvarDecl.getType() instanceof JCArrayTypeTree) {
						psTypeExp = maker.Literal(CTC_BOT, null);
					}else {
						 psTypeExp = memberTypeExpression(typeNode, fvarDecl.getType().toString() + ".class");
					}
					
					JCExpression getInsExp = maker.Apply(List.<JCExpression>nil(), memberTypeExpression(typeNode, "cn.jmicro.api.codec.TypeCoderFactoryUtils.getIns"), List.<JCExpression>nil());
					JCExpression getCoderExp = maker.Apply(List.<JCExpression>nil(), maker.Select(getInsExp, typeNode.toName("getDefaultCoder")), List.<JCExpression>nil());
					JCVariableDecl typeCoderVar = maker.VarDef(maker.Modifiers(0), typeNode.toName("__coder"), typeCoderType, getCoderExp);
					elseStatements = elseStatements.append(typeCoderVar);
					
					//sts = sts.append(typeCoderVar);
					//__coder == null时，取__coder
					//sts = sts.append(maker.If(maker.Binary(CTC_EQUAL, maker.Ident(typeNode.toName("__coder")), gsTypeExp), typeCoderVar, null));
					//JCExpressionStatement asExp = maker.Exec(maker.Assign(maker.Ident(typeNode.toName("__coder")), getCoderExp));
					//sts = sts.append(maker.If(maker.Binary(CTC_EQUAL, maker.Ident(typeNode.toName("__coder")), gsTypeExp),asExp , null));
					
					JCMethodInvocation deExp = maker.Apply(List.<JCExpression>nil(/*paramType,ftype,classType,classType*/), 
						maker.Select(maker.Ident(typeNode.toName("__coder")), decodeName),
						List.<JCExpression>of(bufIntent, psTypeExp, gsTypeExp));
					
					st = maker.Exec(maker.Assign(val, maker.TypeCast(jcftype,deExp)));
					elseStatements = elseStatements.append(st);
				}
				
				JCBlock bl = maker.Block(0, elseStatements);
				
				sts = sts.append(maker.If(isNullExp, setNullVal, bl));
			}
			
		}
		
		JCBlock body = maker.Block(0, sts);
		
		// Override注解
		JCAnnotation overrideAnnotation = maker.Annotation(genJavaLangTypeRef(typeNode, "Override"), List.<JCExpression>nil());
		List<JCAnnotation> annsOnMethod = List.of(overrideAnnotation);
		if (getCheckerFrameworkVersion(typeNode).generateSideEffectFree()) annsOnMethod = annsOnMethod.prepend(maker.Annotation(genTypeRef(typeNode, CheckerFrameworkVersion.NAME__SIDE_EFFECT_FREE), List.<JCExpression>nil()));
		
		// 方法修饰符
		JCModifiers mods = maker.Modifiers(Flags.PUBLIC, List.<JCAnnotation>of(overrideAnnotation));
		
		// JavacHandlerUtil.createRelevantNullableAnnotation(typeNode, var);
		List<JCVariableDecl> vars = List.<JCVariableDecl>of(var);
		
		JCExpression returnType = maker.TypeIdent(CTC_VOID);
		
		JCExpression ioExceptionExp = chainDots(typeNode, "java", "io", "IOException");
		
		JCMethodDecl methodDef = maker.MethodDef(mods, typeNode.toName(methodName), returnType, List.<JCTypeParameter>nil(), vars, List.<JCExpression>of(ioExceptionExp), body, null);
		
		JCMethodDecl decl = JavacHandlerUtil.recursiveSetGeneratedBy(methodDef, source);
		
		//System.out.println(decl.toString());
		
		JCClassDecl type = (JCClassDecl) typeNode.get();
		type.defs = type.defs.append(decl);
		
	}
	
	private List<JCStatement> readVal(JavacNode typeNode, JavacTreeMaker maker, 
		String methodName, JCExpression val, List<JCStatement> sts) {
		
		JCMethodInvocation rexp = maker.Apply(List.<JCExpression>nil(), 
			maker.Select(maker.Ident(typeNode.toName("in")),typeNode.toName(methodName)), List.<JCExpression>nil());
		
		JCExpressionStatement newAssign = maker.Exec(maker.Assign(val, rexp));
		
		return sts.append(newAssign);
	}

	private void createEncodeMethod0(Collection<JavacNode> members, JavacNode typeNode, JavacNode source) {
		
		JavacTreeMaker maker = typeNode.getTreeMaker();
		
		String methodName = "encode";
		
		List<JCStatement> sts = List.<JCStatement>nil();
		
		// JCExpression paramType = memberTypeExpression(typeNode,
		// "java.io.DataOutput");
		JCExpression paramType = chainDots(typeNode, "java", "io", "DataOutput");
		JCExpression bufferType = memberTypeExpression(typeNode, "cn.jmicro.api.codec.JDataOutput");
		JCExpression typeCoderType = memberTypeExpression(typeNode, "cn.jmicro.api.codec.typecoder.ITypeCoder");
		JCExpression serializeObjectType = memberTypeExpression(typeNode, "cn.jmicro.api.codec.ISerializeObject");
		
		// cn.jmicro.api.codec.JDataInput in =
		// (cn.jmicro.api.codec.JDataInput)buf;
		//JCVariableDecl typeCoderVar = maker.VarDef(maker.Modifiers(0), typeNode.toName("__coder"), typeCoderType, null);
		//sts = sts.append(typeCoderVar);
		
		Name bufName = typeNode.toName("buf");
		JCIdent bufIntent = maker.Ident(bufName);
		
		Name encodeName = typeNode.toName(methodName);
		
		long flags = JavacHandlerUtil.addFinalIfNeeded(Flags.PARAMETER, typeNode.getContext());
		JCVariableDecl var = maker.VarDef(maker.Modifiers(flags), bufName, paramType, null);
		// maker.getUnderlyingTreeMaker().VarDef(var1, var2)
		// JCClassDecl cd =(JCClassDecl)typeNode.get();
		
		/*
		 * if(var.sym == null) { var.sym = new VarSymbol(Flags.PARAMETER,
		 * bufName, paramType.type, cd.sym.owner); var.sym.adr = 0; }
		 */
		
		JCTypeCast jdEXp = maker.TypeCast(bufferType, bufIntent);
		JCVariableDecl outVar = maker.VarDef(maker.Modifiers(0), typeNode.toName("out"), bufferType, jdEXp);
		sts = sts.append(outVar);
		
		// cn.jmicro.api.codec.typecoder.ITypeCoder __coder =
		// cn.jmicro.api.codec.TypeCoderFactoryUtils.getIns().getDefaultCoder();\n
		if(!isDirectDescendantOfObject(typeNode)) {
			// super.decode(buf);
			JCTree extending = Javac.getExtendsClause((JCClassDecl) typeNode.get());
			if (extending instanceof JCIdent && 
				((JCIdent)extending).sym.getAnnotation(Serial.class) != null) {
				JCStatement callToSuper = maker.Exec(maker.Apply(List.<JCExpression>nil(), 
					maker.Select(maker.Ident(typeNode.toName("super")), typeNode.toName(methodName)),
					List.<JCExpression>of(bufIntent)));
				sts = sts.append(callToSuper);
			}
		}
		
		for (JavacNode f : members) {
			
			// sb.append(" ").append(fd.getName()).append(" __val"+i).append("=
			// __obj.").append(f.getSimpleName()).append(";\n");
			JCVariableDecl fvarDecl = (JCVariableDecl) f.get();
			JCExpression val = maker.Select(maker.Ident(typeNode.toName("this")), typeNode.toName(f.getName()));
			
			JCExpression isNullExp = maker.Binary(CTC_EQUAL, val, maker.Literal(CTC_BOT, null));
			
			JCStatement st = null;
			String writeMethodName = null;
			
			int i = 0;
			String tname = fvarDecl.getType().type.toString();
			
			if (f.isPrimitive()) {
				switch (((JCPrimitiveTypeTree) fvarDecl.getType()).getPrimitiveTypeKind()) {
				case BOOLEAN:
					writeMethodName = "writeBoolean";
					break;
				case CHAR:
					writeMethodName = "writeChar";
					break;
				case BYTE:
					writeMethodName = "writeByte";
					break;
				case SHORT:
					writeMethodName = "writeShort";
					break;
				case INT:
					writeMethodName = "writeInt";
					break;
				case LONG:
					writeMethodName = "writeLong";
					break;
				case FLOAT:
					writeMethodName = "writeFloat";
					break;
				case DOUBLE:
					writeMethodName = "writeDouble";
					break;
				default:
					throw new RuntimeException("Not support primitive: " + f.toString());
				}
				
				JCStatement ep = maker.Exec(maker.Apply(List.<JCExpression>nil(), 
					maker.Select(maker.Ident(typeNode.toName("out")), typeNode.toName(writeMethodName)), 
					List.<JCExpression>of(val)));
				
				sts = sts.append(ep);
			} else if (tname.equals(Integer.class.getName())) {
				sts = writeVal(typeNode, maker, "writeInt", val, 0, sts, isNullExp);
			} else if (tname.equals(Long.class.getName())) {
				sts = writeVal(typeNode, maker, "writeLong", val, 0, sts, isNullExp);
			} else if (tname.equals(String.class.getName())) {
				// sb.append(" out.writeUTF(").append(" __val"+i).append(" ==
				// null ? \"\" : __val"+i+"); \n");
				sts = writeVal(typeNode, maker, "writeUTF", val, "", sts, isNullExp);
			} else if (tname.equals(Byte.class.getName())) {
				sts = writeVal(typeNode, maker, "writeByte", val, 0, sts, isNullExp);
			} else if (tname.equals(Double.class.getName())) {
				sts = writeVal(typeNode, maker, "writeDouble", val, 0, sts, isNullExp);
			} else if (tname.equals(Float.class.getName())) {
				sts = writeVal(typeNode, maker, "writeFloat", val, 0, sts, isNullExp);
			} else if (tname.equals(Boolean.class.getName())) {
				sts = writeVal(typeNode, maker, "writeBoolean", val, false, sts, isNullExp);
			} else if (tname.equals(Character.class.getName())) {
				sts = writeVal(typeNode, maker, "writeChar", val, 0, sts, isNullExp);
			} else if (tname.equals(Short.class.getName())) {
				sts = writeVal(typeNode, maker, "writeShort", val, 0, sts, isNullExp);
			} else if (tname.equals(Date.class.getName())) {
				// sb.append("if(__val"+i+" == null) buf.writeLong(0L) ;") ;
				// sb.append(" else out.writeLong(").append("
				// __val").append(i).append(".getTime()); \n");
				
				JCExpression curTimeExp = maker.Apply(List.<JCExpression>nil(), maker.Select(val, typeNode.toName("getTime")), List.<JCExpression>nil());
				sts = writeVal(typeNode, maker, "writeLong", curTimeExp, 0, sts, isNullExp);
				
				/*
				 * JCStatement thenpart =maker.Exec(
				 * maker.Apply(List.<JCExpression>nil(),
				 * maker.Select(maker.Ident(typeNode.toName("out")),
				 * typeNode.toName("writeLong")),
				 * List.<JCExpression>of(maker.Literal(0))));
				 * 
				 * JCExpression curTimeExp =
				 * maker.Apply(List.<JCExpression>nil(), maker.Select(val,
				 * typeNode.toName("getTime")), List.<JCExpression>nil());
				 * 
				 * JCStatement elsepart= maker.Exec(
				 * maker.Apply(List.<JCExpression>nil(),
				 * maker.Select(maker.Ident(typeNode.toName("out")),
				 * typeNode.toName("writeLong")),
				 * List.<JCExpression>of(curTimeExp)));
				 * 
				 * st = maker.If(isNullExp, thenpart, elsepart); sts =
				 * sts.append(st);
				 */
				
			} else {
				/*
				 * sb.append("if(__val"+
				 * i+" == null){  out.write(cn.jmicro.api.codec.DecoderConstant.PREFIX_TYPE_NULL); \n} \n"
				 * ) ; sb.
				 * append(" else { out.write(cn.jmicro.api.codec.DecoderConstant.PREFIX_TYPE_PROXY); \n"
				 * ); if(fd.isAnnotationPresent(SO.class)) {
				 * sb.append("java.lang.Object __o"+i).append("=__val"+i).
				 * append("; \n");
				 * sb.append(" ((cn.jmicro.api.codec.ISerializeObject)__o"
				 * +i+").encode(buf);\n }"); } else {
				 * sb.append(" __coder.encode(buf,__val").append(i).append(",").
				 * append(fd.getName()).append(".class,").append(" null ); \n }"
				 * ); }
				 */
				
				JCExpression nullPrefix = chainDots(typeNode, "cn", "jmicro", "api", "codec", "DecoderConstant", "PREFIX_TYPE_NULL");
				JCExpression proxyPrefix = chainDots(typeNode, "cn", "jmicro", "api", "codec", "DecoderConstant", "PREFIX_TYPE_PROXY");
				
				// sb.append("if(__val"+i+" == null){
				// out.write(cn.jmicro.api.codec.DecoderConstant.PREFIX_TYPE_NULL);
				// \n} \n") ;
				JCStatement thenpart = maker.Exec(maker.Apply(List.<JCExpression>nil(), maker.Select(maker.Ident(typeNode.toName("out")), typeNode.toName("write")), List.<JCExpression>of(nullPrefix)));
				
				// sb.append(" else {
				// out.write(cn.jmicro.api.codec.DecoderConstant.PREFIX_TYPE_PROXY);
				// \n");
				
				List<JCStatement> encodeObjectSts = List.<JCStatement>nil();
				
				JCStatement elsepart = maker.Exec(maker.Apply(List.<JCExpression>nil(), maker.Select(maker.Ident(typeNode.toName("out")), typeNode.toName("write")), List.<JCExpression>of(proxyPrefix)));
				
				encodeObjectSts = encodeObjectSts.append(elsepart);
				
				JCExpression jcType = (JCExpression)fvarDecl.getType();
				if (jcType instanceof JCIdent && 
					((JCIdent)jcType).sym.getAnnotation(Serial.class) != null/*f.hasAnnotation(Serial.class)*/) {
					
					// sb.append("
					// ((cn.jmicro.api.codec.ISerializeObject)__o"+i+").encode(buf);\n
					// }");
					String loName = "lo" + i++;
					
					// sb.append("java.lang.Object
					// __o"+i).append("=__val"+i).append("; \n");
					JCVariableDecl objVar = maker.VarDef(maker.Modifiers(0), typeNode.toName(loName), genJavaLangTypeRef(typeNode, "Object"), val);
					
					encodeObjectSts = encodeObjectSts.append(objVar);
					
					// ((cn.jmicro.api.codec.ISerializeObject)__o"+i+")
					JCTypeCast tc = maker.TypeCast(serializeObjectType,maker.Ident(typeNode.toName(loName)));
					
					// .encode(buf);\n
					st = maker.Exec(maker.Apply(List.<JCExpression>nil(), maker.Select(tc, encodeName), List.<JCExpression>of(bufIntent)));
					encodeObjectSts = encodeObjectSts.append(st);
				} else {
					// sb.append("
					// __coder.encode(buf,__val").append(i).append(",").append(fd.getName()).append(".class,").append("
					// null ); \n }");
					
					// JCExpression classType =
					// genJavaLangTypeRef(typeNode,"Class");
					//JCExpression classType = chainDots(typeNode, "java", "lang", "Class");
					JCExpression psTypeExp = null;
					JCExpression gsTypeExp = maker.Literal(CTC_BOT, null);
					
					if(jcType instanceof ParameterizedTypeTree) {
						ParameterizedTypeTree pjcType = (ParameterizedTypeTree)jcType;
						psTypeExp = memberTypeExpression(typeNode, pjcType.getType().toString() + ".class");
					}else if(jcType instanceof JCArrayTypeTree) {
						psTypeExp = maker.Literal(CTC_BOT, null);
					}else {
						 psTypeExp = memberTypeExpression(typeNode, fvarDecl.getType().toString() + ".class");
					}
					
					JCExpression getInsExp = maker.Apply(List.<JCExpression>nil(), memberTypeExpression(typeNode, "cn.jmicro.api.codec.TypeCoderFactoryUtils.getIns"), List.<JCExpression>nil());
					JCExpression getCoderExp = maker.Apply(List.<JCExpression>nil(), maker.Select(getInsExp, typeNode.toName("getDefaultCoder")), List.<JCExpression>nil());
					JCVariableDecl typeCoderVar = maker.VarDef(maker.Modifiers(0), typeNode.toName("__coder"), typeCoderType, getCoderExp);
					encodeObjectSts = encodeObjectSts.append(typeCoderVar);
					
					//JCExpressionStatement asExp = maker.Exec(maker.Assign(maker.Ident(typeNode.toName("__coder")), getCoderExp));
					//__coder == null 取__coder
					//sts = sts.append(maker.If(maker.Binary(CTC_EQUAL, maker.Ident(typeNode.toName("__coder")), gsTypeExp),asExp , null));
					
					st = maker.Exec(maker.Apply(List.<JCExpression>nil(/*paramType,ftype,classType,classType*/),
						maker.Select(maker.Ident(typeNode.toName("__coder")), encodeName), 
						List.<JCExpression>of(bufIntent, val, psTypeExp, gsTypeExp)));
					encodeObjectSts = encodeObjectSts.append(st);
				}
				
				JCBlock bl = maker.Block(0, encodeObjectSts);
				
				sts = sts.append(maker.If(isNullExp, thenpart, bl));
			}
			
		}
		
		JCBlock body = maker.Block(0, sts);
		
		// Override注解
		JCAnnotation overrideAnnotation = maker.Annotation(genJavaLangTypeRef(typeNode, "Override"), List.<JCExpression>nil());
		List<JCAnnotation> annsOnMethod = List.of(overrideAnnotation);
		if (getCheckerFrameworkVersion(typeNode).generateSideEffectFree()) annsOnMethod = annsOnMethod.prepend(maker.Annotation(genTypeRef(typeNode, CheckerFrameworkVersion.NAME__SIDE_EFFECT_FREE), List.<JCExpression>nil()));
		
		// 方法修饰符
		JCModifiers mods = maker.Modifiers(Flags.PUBLIC, List.<JCAnnotation>of(overrideAnnotation));
		
		// JavacHandlerUtil.createRelevantNullableAnnotation(typeNode, var);
		List<JCVariableDecl> vars = List.<JCVariableDecl>of(var);
		
		JCExpression returnType = maker.TypeIdent(CTC_VOID);
		
		JCExpression ioExceptionExp = chainDots(typeNode, "java", "io", "IOException");
		
		JCMethodDecl methodDef = maker.MethodDef(mods, typeNode.toName(methodName), returnType, List.<JCTypeParameter>nil(), vars, List.<JCExpression>of(ioExceptionExp), body, null);
		
		JCMethodDecl decl = JavacHandlerUtil.recursiveSetGeneratedBy(methodDef, source);
		
		//System.out.println(decl.toString());
		
		JCClassDecl type = (JCClassDecl) typeNode.get();
		type.defs = type.defs.append(decl);
		
	}
	
	private List<JCStatement> writeVal(JavacNode typeNode, JavacTreeMaker maker, String writeMethodName, JCExpression val, Object devVal, List<JCStatement> sts, JCExpression isNullExp) {
		
		JCStatement thenpart = maker.Exec(maker.Apply(List.<JCExpression>nil(), maker.Select(maker.Ident(typeNode.toName("out")), typeNode.toName(writeMethodName)), List.<JCExpression>of(maker.Literal(devVal))));
		
		JCStatement ep = maker.Exec(maker.Apply(List.<JCExpression>nil(), maker.Select(maker.Ident(typeNode.toName("out")), typeNode.toName(writeMethodName)), List.<JCExpression>of(val)));
		
		JCStatement st = maker.If(isNullExp, thenpart, ep);
		sts = sts.append(st);
		
		return sts;
	}
	
	/**
	 * 创建 域/方法 的多级访问, 方法的标识只能是最后一个 . 运算符
	 * 
	 * @param components
	 * @return
	 */
	private JCExpression memberTypeExpression(JavacNode typeNode, String components) {
		String[] componentArray = components.split("\\.");
		JCTree.JCExpression expr = typeNode.getTreeMaker().Ident(typeNode.toName(componentArray[0]));
		for (int i = 1; i < componentArray.length; i++) {
			expr = typeNode.getTreeMaker().Select(expr, typeNode.toName(componentArray[i]));
		}
		return expr;
	}
	
	public String getTypeName(JavacNode typeNode) {
		String typeName = typeNode.getName();
		JavacNode upType = typeNode.up();
		while (upType.getKind() == Kind.TYPE && !upType.getName().isEmpty()) {
			typeName = upType.getName() + "." + typeName;
			upType = upType.up();
		}
		return typeName;
	}
}
