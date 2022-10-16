/*
 * Copyright (C) 2009-2022 The Project Lombok Authors.
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
package lombok.eclipse.handlers;

import static lombok.eclipse.handlers.EclipseHandlerUtil.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.AllocationExpression;
import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.ast.Argument;
import org.eclipse.jdt.internal.compiler.ast.ArrayTypeReference;
import org.eclipse.jdt.internal.compiler.ast.Assignment;
import org.eclipse.jdt.internal.compiler.ast.Block;
import org.eclipse.jdt.internal.compiler.ast.CastExpression;
import org.eclipse.jdt.internal.compiler.ast.CharLiteral;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.DoubleLiteral;
import org.eclipse.jdt.internal.compiler.ast.EqualExpression;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.FalseLiteral;
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration;
import org.eclipse.jdt.internal.compiler.ast.FieldReference;
import org.eclipse.jdt.internal.compiler.ast.FloatLiteral;
import org.eclipse.jdt.internal.compiler.ast.IfStatement;
import org.eclipse.jdt.internal.compiler.ast.InstanceOfExpression;
import org.eclipse.jdt.internal.compiler.ast.IntLiteral;
import org.eclipse.jdt.internal.compiler.ast.Literal;
import org.eclipse.jdt.internal.compiler.ast.LocalDeclaration;
import org.eclipse.jdt.internal.compiler.ast.LongLiteral;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.NameReference;
import org.eclipse.jdt.internal.compiler.ast.NullLiteral;
import org.eclipse.jdt.internal.compiler.ast.OperatorIds;
import org.eclipse.jdt.internal.compiler.ast.QualifiedTypeReference;
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference;
import org.eclipse.jdt.internal.compiler.ast.Statement;
import org.eclipse.jdt.internal.compiler.ast.StringLiteral;
import org.eclipse.jdt.internal.compiler.ast.SuperReference;
import org.eclipse.jdt.internal.compiler.ast.ThisReference;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.lookup.TypeConstants;
import org.eclipse.jdt.internal.compiler.lookup.TypeIds;

import lombok.AccessLevel;
import lombok.Serial;
import lombok.core.AST.Kind;
import lombok.core.AnnotationValues;
import lombok.core.ImportList;
import lombok.core.configuration.CheckerFrameworkVersion;
import lombok.core.configuration.NullAnnotationLibrary;
import lombok.core.handlers.HandlerUtil;
import lombok.eclipse.Eclipse;
import lombok.eclipse.EclipseAnnotationHandler;
import lombok.eclipse.EclipseNode;
import lombok.eclipse.handlers.EclipseHandlerUtil.MemberExistsResult;
import lombok.spi.Provides;

/**
 * Handles the {@code lombok.Getter} annotation for eclipse.
 */
@Provides
public class HandleSerial extends EclipseAnnotationHandler<Serial> {
	
	public void handle(AnnotationValues<Serial> annotation, Annotation ast, EclipseNode annotationNode) {
		//handleFlagUsage(annotationNode, ConfigurationKeys.GETTER_FLAG_USAGE, "@Getter");
		
		List<Annotation> onParam = unboxAndRemoveAnnotationParameter(ast, "", "@Serial(", annotationNode);
		EclipseNode typeNode = annotationNode.up();
		//Serial annotationInstance = annotation.getInstance();
		
		if(typeNode == null) return;
		
		MemberExistsResult encodeExists = methodExists("encode", typeNode, 1);
		if(encodeExists == MemberExistsResult.NOT_EXISTS) {
			createEncodeMethod(typeNode,annotationNode, ast);
		}
		
		MemberExistsResult decodeCodeExists = methodExists("decode", typeNode, 1);
		if(decodeCodeExists == MemberExistsResult.NOT_EXISTS) {
			createDecodeMethod(typeNode,annotationNode, ast);
		}
		
		String cn = "cn.jmicro.api.codec.ISerializeObject";
		TypeReference serialTypreRef = EclipseHandlerUtil.createTypeReference(cn, ast);
		TypeDeclaration typeDecl = (TypeDeclaration)typeNode.get();
		TypeReference[] tr = null;
		if(typeDecl.superInterfaces != null && typeDecl.superInterfaces.length > 0) {
			tr = new TypeReference[typeDecl.superInterfaces.length + 1];
			System.arraycopy(typeDecl.superInterfaces, 0, tr, 0, typeDecl.superInterfaces.length);
			tr[typeDecl.superInterfaces.length] = serialTypreRef;
		}else {
			tr = new TypeReference[1];
			tr[0] = serialTypreRef;
		}
		typeDecl.superInterfaces = tr;
		
		
		/*if(!implementSerialInterface(typeDecl, ast, cn, typeNode.getImportList())) {
			TypeReference serialTypreRef = EclipseHandlerUtil.createTypeReference(cn, ast);
			setGeneratedBy(serialTypreRef, ast);
			TypeReference[] tr = null;
			if(typeDecl.superInterfaces != null && typeDecl.superInterfaces.length > 0) {
				tr = new TypeReference[typeDecl.superInterfaces.length + 1];
				System.arraycopy(typeDecl.superInterfaces, 0, tr, 0, typeDecl.superInterfaces.length);
				tr[typeDecl.superInterfaces.length] = serialTypreRef;
			}else {
				tr = new TypeReference[1];
				tr[0] = serialTypreRef;
			}
		}*/
		
	}
	
	private boolean implementSerialInterface(TypeDeclaration typeDecl, ASTNode ast,String className,ImportList il) {
		if(typeDecl == null) {
			return false;
		}
		
		//boolean isImpSerail = false;
		//cn.jmicro.api.codec.ISerializeObject
		//ImportList il = typeNode.getAst().getImportList();
		
		String simpleName = className;
		int idx = className.lastIndexOf(".");
		if(idx > 0) {
			simpleName = simpleName.substring(idx+1);
		}
		
		String fullClassName = il.getFullyQualifiedNameForSimpleNameNoAliasing(simpleName);
		if(fullClassName != null && fullClassName.equals(className)) {
			return true;
		}else {
			return false;//implementSerialInterface(typeDecl.superclass, ast, className, il);
		}
	}

	private void createDecodeMethod(EclipseNode typeNode, EclipseNode errorNode, ASTNode source) {

		int pS = source.sourceStart;
		int pE = source.sourceEnd;
		long p = (long) pS << 32 | pE;
		
		//List<NullAnnotationLibrary> applied = new ArrayList<NullAnnotationLibrary>();
		char[] bufCharArr = new char[] {'b','u','f'};
		
		//定义方法头部public void encode(final java.io.DataOutput buf) throws java.io.IOException
		MethodDeclaration method = new MethodDeclaration(((CompilationUnitDeclaration) typeNode.top().get()).compilationResult);
		setGeneratedBy(method, source);
		method.modifiers = toEclipseModifier(AccessLevel.PUBLIC);
		
		//无返回值
		method.returnType = TypeReference.baseTypeReference(TypeIds.T_void, 0);
		method.returnType.sourceStart = pS;
		method.returnType.sourceEnd = pE;
		setGeneratedBy(method.returnType, source);
		Annotation overrideAnnotation = makeMarkerAnnotation(TypeConstants.JAVA_LANG_OVERRIDE, source);
		if (getCheckerFrameworkVersion(typeNode).generateSideEffectFree()) {
			method.annotations = new Annotation[] {overrideAnnotation, generateNamedAnnotation(source, CheckerFrameworkVersion.NAME__SIDE_EFFECT_FREE)};
		} else {
			method.annotations = new Annotation[] {overrideAnnotation};
		}
		method.selector = "decode".toCharArray();
		
		//抛出IO异常 java.io.IOException
		QualifiedTypeReference ioException = new QualifiedTypeReference(TypeConstants.JAVA_IO_IOEXCEPTION, new long[] {p, p, p});
		setGeneratedBy(ioException, source);
		method.thrownExceptions = new TypeReference[]{ioException};
		
		method.typeParameters = null;
		method.bits |= Eclipse.ECLIPSE_DO_NOT_TOUCH_FLAG;
		method.bodyStart = method.declarationSourceStart = method.sourceStart = source.sourceStart;
		method.bodyEnd = method.declarationSourceEnd = method.sourceEnd = source.sourceEnd;
		
		///java.io.DataOutput 方法参数
		char[][] dataOutputChart = {TypeConstants.JAVA, TypeConstants.IO, "DataInput".toCharArray()};
		QualifiedTypeReference dataOutputRef = new QualifiedTypeReference(dataOutputChart, new long[] {p, p, p});
		setGeneratedBy(dataOutputRef, source);
		
		method.arguments = new Argument[] {new Argument(bufCharArr, 0, dataOutputRef, 0)};
		method.arguments[0].sourceStart = pS;
		method.arguments[0].sourceEnd = pE;
		//EclipseHandlerUtil.createRelevantNullableAnnotation(typeNode, method.arguments[0], method, applied);
		setGeneratedBy(method.arguments[0], source);
		
		List<Statement> statements = new ArrayList<Statement>();
		
		TypeDeclaration typeDecl = (TypeDeclaration)typeNode.get();
		
		if(!isDirectDescendantOfObject(typeNode)) {
			//super.decode(buf);
			//String nearest = scanForNearestAnnotation(typeNode, Serial.class.getName());
			if(typeDecl.superclass != null && typeDecl.superclass.toString().endsWith("JRso")) {
				MessageSend callToSuper = new MessageSend();
				callToSuper.sourceStart = pS; callToSuper.sourceEnd = pE;
				setGeneratedBy(callToSuper, source);
				callToSuper.receiver = new SuperReference(pS, pE);
				setGeneratedBy(callToSuper.receiver, source);
				callToSuper.selector = "decode".toCharArray();
				SingleNameReference oRef = new SingleNameReference(bufCharArr, p);
				setGeneratedBy(oRef, source);
				callToSuper.arguments = new Expression[] {oRef};
				statements.add(callToSuper);
			}
		}
		
		//cn.jmicro.api.codec.JDataInput out = (cn.jmicro.api.codec.JDataInput)buf;
		{
			//String cn = "cn.jmicro.api.codec.JDataInput";
			TypeReference jdataInputRef = EclipseHandlerUtil.createTypeReference("cn.jmicro.api.codec.JDataInput", source);
			setGeneratedBy(jdataInputRef, source);
			
			//cn.jmicro.api.codec.JDataInput out 
			LocalDeclaration other = new LocalDeclaration(new char[] {'i','n'}, pS, pE);
			other.modifiers |= ClassFileConstants.AccFinal;
			setGeneratedBy(other, source);
			other.type = jdataInputRef;
			
			// (cn.jmicro.api.codec.JDataInput)buf
			NameReference oRef = new SingleNameReference(bufCharArr, p);
			setGeneratedBy(oRef, source);
			//TypeReference targetType = copyType(jdataOutputRef);
			//setGeneratedBy(targetType, source);
			other.initialization = makeCastExpression(oRef, jdataInputRef, source);
			statements.add(other);
		}
		
		List<EclipseNode> members = new ArrayList<>();
		for(EclipseNode child : typeNode.down()) {
			if (child.getKind() == Kind.FIELD && !child.isStatic() && !child.isTransient()) {
				members.add(child);
			}
		}
		
		if(members.size() > 1) {
			members.sort(new Comparator<EclipseNode>() {
				@Override 
				public int compare(EclipseNode s1, EclipseNode s2) {
					return s1.getName().compareTo(s2.getName());
				}
			});
		}
		
		int i = 0;
		
		for (EclipseNode f : members) {
			String readMethodName = null;
			
			//FieldDeclaration fd = (FieldDeclaration)f.get();
			//TypeReference fieldTypeRef = fd.type;
			
			TypeReference fType = getFieldType(f, HandlerUtil.FieldAccess.PREFER_FIELD);
			
			boolean isArray = fType instanceof ArrayTypeReference;
			
			if(!isArray && f.isPrimitive()) {
				char[] token = fType.getLastToken();
				if(Arrays.equals(TypeConstants.BOOLEAN, token)) {
					readMethodName = "readBoolean";
				}else if(Arrays.equals(TypeConstants.CHAR, token)) {
					readMethodName = "readChar";
				}else if(Arrays.equals(TypeConstants.BYTE, token)) {
					readMethodName = "readByte";
				}else if(Arrays.equals(TypeConstants.SHORT, token)) {
					readMethodName = "readShort";
				}else if(Arrays.equals(TypeConstants.INT, token)) {
					readMethodName = "readInt";
				}else if(Arrays.equals(TypeConstants.LONG, token)) {
					readMethodName = "readLong";
				}else if(Arrays.equals(TypeConstants.FLOAT, token)) {
					readMethodName = "readFloat";
				}else if(Arrays.equals(TypeConstants.DOUBLE, token)) {
					readMethodName = "readDouble";
				}else {
					throw new RuntimeException("Not support primitive: " + f.toString());
				}
				readVal(readMethodName,f.getName(),pS, pE, source,statements);
			} else {
				
				FieldReference fieldInThis = new FieldReference(f.getName().toCharArray(), p);
				setGeneratedBy(fieldInThis, source);
				fieldInThis.receiver = new ThisReference(pS, pE);
				setGeneratedBy(fieldInThis.receiver, source);

				if (!isArray &&  EclipseHandlerUtil.typeMatches(Integer.class, f, fType)) {
					readVal("readInt",f.getName(),pS, pE, source,statements);
				} else if(!isArray && EclipseHandlerUtil.typeMatches(String.class, f, fType)) {
					readVal("readUTF", f.getName(),pS, pE, source, statements);
				}  else if(!isArray && EclipseHandlerUtil.typeMatches(Long.class, f, fType)) {
					readVal("readLong",f.getName(), pS, pE, source,statements);
				} else if(!isArray && EclipseHandlerUtil.typeMatches(Short.class, f, fType)) {
					readVal("readShort", f.getName(),pS, pE, source,statements);
				}else if(!isArray && EclipseHandlerUtil.typeMatches(Byte.class, f, fType)) {
					readVal("readByte",f.getName(), pS, pE, source,statements);
				} else if(!isArray && EclipseHandlerUtil.typeMatches(Double.class, f, fType)) {
					readVal("readDouble",f.getName(), pS, pE, source,statements);
				} else if(!isArray && EclipseHandlerUtil.typeMatches(Float.class, f, fType)) {
					readVal("readFloat", f.getName(), pS, pE, source,statements);
				} else if(!isArray && EclipseHandlerUtil.typeMatches(Boolean.class, f, fType)) {
					readVal("readBoolean",  f.getName(),pS, pE, source,statements);
				} else if(!isArray && EclipseHandlerUtil.typeMatches(Character.class, f, fType)) {
					readVal("readChar", f.getName(),pS, pE, source,statements);
				} else if(!isArray && EclipseHandlerUtil.typeMatches(Date.class, f, fType)) {
					//取得长整数时间
					final char[][] JAVA_UTIL_DATE = {
						{'j', 'a', 'v', 'a'}, {'u', 't', 'i', 'l'}, {'D', 'a', 't', 'e'}
					};
					
					//this.fieldName = new java.util.Date();
					final long[] NULL_POSS = {0L};
					TypeReference v1Type = new QualifiedTypeReference(JAVA_UTIL_DATE, NULL_POSS);
					setGeneratedBy(v1Type, source);
					//v1Type = addTypeArgs(1, false, builderType, v1Type, data.getTypeArgs());
					AllocationExpression newDate = new AllocationExpression();
					newDate.type = v1Type;
					setGeneratedBy(newDate, source);
					Assignment ass = new Assignment(fieldInThis, newDate, pE);
					setGeneratedBy(ass, source);
					statements.add(ass);
					
					MessageSend setTime = new MessageSend();
					setTime.sourceStart = pS; 
					setTime.sourceEnd = pE;
					setGeneratedBy(setTime, source);
					setTime.receiver = fieldInThis;
					setTime.selector = "setTime".toCharArray();
					
					MessageSend readLongVal = new MessageSend();
					readLongVal.sourceStart = pS; 
					readLongVal.sourceEnd = pE;
					setGeneratedBy(readLongVal, source);
					readLongVal.receiver = new SingleNameReference(new char[] {'i','n'},p);
					setGeneratedBy(readLongVal.receiver, source);
					readLongVal.selector = "readLong".toCharArray();
					
					setTime.arguments = new Expression[] {readLongVal};
					statements.add(setTime);
					
				}else {
					/*
					  if(in.readByte() == cn.jmicro.api.codec.DecoderConstant.PREFIX_TYPE_NULL) this.pers = null; 
					  else {
				            cn.jmicro.api.codec.typecoder.ITypeCoder __coder = cn.jmicro.api.codec.TypeCoderFactoryUtils.getIns().getDefaultCoder();
				            this.pers = (Set<Integer>)__coder.decode(buf, Set.class, null);
				        }
					 */
					//cn.jmicro.api.codec.DecoderConstant.PREFIX_TYPE_NULL
					if(!isArray) {
						char[][] DecoderConstantRef = {{'c','n'}, "jmicro".toCharArray(),"api".toCharArray(),"codec".toCharArray(),"DecoderConstant".toCharArray()};
						
						//PREFIX_TYPE_NULL
						FieldReference nullTypePrefix = new FieldReference("PREFIX_TYPE_NULL".toCharArray(), p);
						nullTypePrefix.receiver = generateQualifiedNameRef(source, DecoderConstantRef);
						setGeneratedBy(nullTypePrefix, source);
						
						//in.readByte()
						MessageSend prefixCodeVal = new MessageSend();
						prefixCodeVal.sourceStart = pS;
						prefixCodeVal.sourceEnd = pE;
						setGeneratedBy(prefixCodeVal, source);
						prefixCodeVal.receiver = new SingleNameReference(new char[] {'i','n'},p);
						setGeneratedBy(prefixCodeVal.receiver, source);
						prefixCodeVal.selector = "readByte".toCharArray();
						
						//in.readByte() == cn.jmicro.api.codec.DecoderConstant.PREFIX_TYPE_NULL
						EqualExpression isNullPrefixCodeVal = new EqualExpression(nullTypePrefix, prefixCodeVal, OperatorIds.EQUAL_EQUAL);
						setGeneratedBy(isNullPrefixCodeVal, source);

						//返回空值 this.fileName = null;
						Assignment thenSetNullSt = new Assignment(fieldInThis, new NullLiteral(pS, pE), pE);
						setGeneratedBy(thenSetNullSt, source);
						setGeneratedBy(thenSetNullSt.expression, source);
						
						//else写值
						
						//cn.jmicro.api.codec.TypeCoderFactoryUtils.getIns().getDefaultCoder().decode(buf, this.tags, Set.class, null);
						char[][] TypeCoderFactoryUtils = {{'c','n'}, "jmicro".toCharArray(),"api".toCharArray(),"codec".toCharArray(),"TypeCoderFactoryUtils".toCharArray()};
						MessageSend getIns = new MessageSend();
						getIns.sourceStart = pS; 
						getIns.sourceEnd = pE;
						setGeneratedBy(getIns, source);
						getIns.receiver = generateQualifiedNameRef(source, TypeCoderFactoryUtils);
						setGeneratedBy(getIns.receiver, source);
						getIns.selector = "getIns".toCharArray();
						getIns.arguments = null;
						
						MessageSend getDefaultCoder = new MessageSend();
						getDefaultCoder.sourceStart = pS;
						getDefaultCoder.sourceEnd = pE;
						setGeneratedBy(getDefaultCoder, source);
						getDefaultCoder.receiver = getIns;
						setGeneratedBy(getDefaultCoder.receiver, source);
						getDefaultCoder.selector = "getDefaultCoder".toCharArray();
						getDefaultCoder.arguments = null;
						
						MessageSend decodeVal = new MessageSend();
						decodeVal.sourceStart = pS;
						decodeVal.sourceEnd = pE;
						setGeneratedBy(decodeVal, source);
						decodeVal.receiver = getDefaultCoder;
						setGeneratedBy(decodeVal.receiver, source);
						decodeVal.selector = "decode".toCharArray();
						decodeVal.arguments = new Expression[3];
						decodeVal.arguments[0] = new SingleNameReference(new char[] {'b','u','f'},p);
						setGeneratedBy(decodeVal.arguments[0], source);
						
						/*char[][] clazzCharArray = {fType.toString().toCharArray(),"class".toCharArray()};
						decodeVal.arguments[1] = generateQualifiedNameRef(source,clazzCharArray);*/
						decodeVal.arguments[1] = new NullLiteral(pS,pE) ;
						
						decodeVal.arguments[2] = new NullLiteral(pS,pE) ;
						setGeneratedBy(decodeVal.arguments[2], source);
						
						CastExpression rval = makeCastExpression(decodeVal, EclipseHandlerUtil.copyType(fType,source), source);
						setGeneratedBy(rval, source);
						
						Assignment setReadVal = new Assignment(fieldInThis, rval, pE);
						setGeneratedBy(thenSetNullSt, source);
						setGeneratedBy(thenSetNullSt.expression, source);
						//cn.jmicro.api.codec.TypeCoderFactoryUtils.getIns().getDefaultCoder().decode(buf, this.tags, Set.class, null); end
						
						
						String cn0 = "cn.jmicro.api.codec.ISerializeObject";
						TypeReference serialTypreRef = EclipseHandlerUtil.createTypeReference(cn0, source);
						setGeneratedBy(serialTypreRef, source);
						
						// ((ISerializeObject)this.fieldName).decode(buff);
						Block decodeDefBlock = new Block(0);
						setGeneratedBy(decodeDefBlock, source);
						decodeDefBlock.statements = new Statement[2];
						
						String bn = "b"+i++;
						
						LocalDeclaration other = new LocalDeclaration(bn.toCharArray(), pS, pE);
						other.modifiers |= ClassFileConstants.AccFinal;
						setGeneratedBy(other, source);
						other.type = serialTypreRef;
						other.initialization = makeCastExpression(fieldInThis, serialTypreRef, source);
						decodeDefBlock.statements[0] = other;
						
						NameReference oRef = new SingleNameReference(bn.toCharArray(), p);
						setGeneratedBy(oRef, source);
						
						MessageSend serialEncodeObjCall = new MessageSend();
						serialEncodeObjCall.sourceStart = pS; serialEncodeObjCall.sourceEnd = pE;
						setGeneratedBy(serialEncodeObjCall, source);
						serialEncodeObjCall.receiver = oRef;
						serialEncodeObjCall.selector = "decode".toCharArray();
						SingleNameReference buff = new SingleNameReference(bufCharArr, p);
						setGeneratedBy(buff, source);
						serialEncodeObjCall.arguments = new Expression[] {buff};
						
						decodeDefBlock.statements[1] = serialEncodeObjCall;
						// ((ISerializeObject)this.fieldName).encode(buff); end
						
						//if(this.fileName instanceof ISerializeObject)
						InstanceOfExpression insOf = new InstanceOfExpression(fieldInThis,serialTypreRef);
						
						IfStatement instanceofStatement = new IfStatement(insOf, decodeDefBlock, setReadVal, pS, pE);
						setGeneratedBy(instanceofStatement, source);
						
						
						IfStatement ifOtherEqualsThis = new IfStatement(isNullPrefixCodeVal, thenSetNullSt, instanceofStatement, pS, pE);
						setGeneratedBy(ifOtherEqualsThis, source);
						
						statements.add(ifOtherEqualsThis);
					} else {
						
						char[][] DecoderConstantRef = {{'c','n'}, "jmicro".toCharArray(),"api".toCharArray(),"codec".toCharArray(),"DecoderConstant".toCharArray()};
						
						//PREFIX_TYPE_NULL
						FieldReference nullTypePrefix = new FieldReference("PREFIX_TYPE_NULL".toCharArray(), p);
						nullTypePrefix.receiver = generateQualifiedNameRef(source, DecoderConstantRef);
						setGeneratedBy(nullTypePrefix, source);
						
						//in.readByte()
						MessageSend prefixCodeVal = new MessageSend();
						prefixCodeVal.sourceStart = pS;
						prefixCodeVal.sourceEnd = pE;
						setGeneratedBy(prefixCodeVal, source);
						prefixCodeVal.receiver = new SingleNameReference(new char[] {'i','n'},p);
						setGeneratedBy(prefixCodeVal.receiver, source);
						prefixCodeVal.selector = "readByte".toCharArray();
						
						//in.readByte() == cn.jmicro.api.codec.DecoderConstant.PREFIX_TYPE_NULL
						EqualExpression isNullPrefixCodeVal = new EqualExpression(nullTypePrefix, prefixCodeVal, OperatorIds.EQUAL_EQUAL);
						setGeneratedBy(isNullPrefixCodeVal, source);

						//返回空值 this.fileName = null;
						Assignment thenSetNullSt = new Assignment(fieldInThis, new NullLiteral(pS, pE), pE);
						setGeneratedBy(thenSetNullSt, source);
						setGeneratedBy(thenSetNullSt.expression, source);
						
						//else写值
						
						//cn.jmicro.api.codec.TypeCoderFactoryUtils.getIns().getDefaultCoder().decode(buf, this.tags, Set.class, null);
						char[][] TypeCoderFactoryUtils = {{'c','n'}, "jmicro".toCharArray(),"api".toCharArray(),"codec".toCharArray(),"TypeCoderFactoryUtils".toCharArray()};
						MessageSend getIns = new MessageSend();
						getIns.sourceStart = pS; 
						getIns.sourceEnd = pE;
						setGeneratedBy(getIns, source);
						getIns.receiver = generateQualifiedNameRef(source, TypeCoderFactoryUtils);
						setGeneratedBy(getIns.receiver, source);
						getIns.selector = "getIns".toCharArray();
						getIns.arguments = null;
						
						MessageSend getDefaultCoder = new MessageSend();
						getDefaultCoder.sourceStart = pS;
						getDefaultCoder.sourceEnd = pE;
						setGeneratedBy(getDefaultCoder, source);
						getDefaultCoder.receiver = getIns;
						setGeneratedBy(getDefaultCoder.receiver, source);
						getDefaultCoder.selector = "getDefaultCoder".toCharArray();
						getDefaultCoder.arguments = null;
						
						MessageSend decodeVal = new MessageSend();
						decodeVal.sourceStart = pS;
						decodeVal.sourceEnd = pE;
						setGeneratedBy(decodeVal, source);
						decodeVal.receiver = getDefaultCoder;
						setGeneratedBy(decodeVal.receiver, source);
						decodeVal.selector = "decode".toCharArray();
						decodeVal.arguments = new Expression[3];
						decodeVal.arguments[0] = new SingleNameReference(new char[] {'b','u','f'},p);
						setGeneratedBy(decodeVal.arguments[0], source);
						
						/*char[][] clazzCharArray = {fType.toString().toCharArray(),"class".toCharArray()};
						decodeVal.arguments[1] = generateQualifiedNameRef(source,clazzCharArray);*/
						decodeVal.arguments[1] = new NullLiteral(pS,pE) ;
						
						decodeVal.arguments[2] = new NullLiteral(pS,pE) ;
						setGeneratedBy(decodeVal.arguments[2], source);
						
						CastExpression rval = makeCastExpression(decodeVal, EclipseHandlerUtil.copyType(fType,source), source);
						setGeneratedBy(rval, source);
						
						Assignment setReadVal = new Assignment(fieldInThis, rval, pE);
						setGeneratedBy(thenSetNullSt, source);
						setGeneratedBy(thenSetNullSt.expression, source);
						//cn.jmicro.api.codec.TypeCoderFactoryUtils.getIns().getDefaultCoder().decode(buf, this.tags, Set.class, null); end
						
						
						String cn0 = "cn.jmicro.api.codec.ISerializeObject";
						TypeReference serialTypreRef = EclipseHandlerUtil.createTypeReference(cn0, source);
						setGeneratedBy(serialTypreRef, source);
						
						IfStatement ifOtherEqualsThis = new IfStatement(isNullPrefixCodeVal, thenSetNullSt, setReadVal, pS, pE);
						setGeneratedBy(ifOtherEqualsThis, source);
						
						statements.add(ifOtherEqualsThis);
					}
					
				}
				
			}
		}
		
		method.statements = statements.toArray(new Statement[0]);
		method.traverse(new SetGeneratedByVisitor(errorNode.get()), ((TypeDeclaration)typeNode.get()).scope);
		injectMethod(typeNode, method);
		
	}

	private void readVal(String readMethod,String fieldName, int pS, int pE, ASTNode source, List<Statement> statements) {
		// TODO Auto-generated method stub
		long p = (long) pS << 32 | pE;
		FieldReference fieldInThis = new FieldReference(fieldName.toCharArray(), p);
		setGeneratedBy(fieldInThis, source);
		fieldInThis.receiver = new ThisReference(pS, pE);
		setGeneratedBy(fieldInThis.receiver, source);
		
		//val== null 写默认值
		MessageSend thenWriteVal = new MessageSend();
		thenWriteVal.sourceStart = pS; 
		thenWriteVal.sourceEnd = pE;
		setGeneratedBy(thenWriteVal, source);
		thenWriteVal.receiver = new SingleNameReference(new char[] {'i','n'},p);
		setGeneratedBy(thenWriteVal.receiver, source);
		thenWriteVal.selector = readMethod.toCharArray();
		
		Assignment ass = new Assignment(fieldInThis, thenWriteVal, pE);
		
		statements.add(ass);
		
	}

	private void createEncodeMethod(EclipseNode typeNode, EclipseNode errorNode, ASTNode source) {
		int pS = source.sourceStart;
		int pE = source.sourceEnd;
		long p = (long) pS << 32 | pE;
		
		List<NullAnnotationLibrary> applied = new ArrayList<NullAnnotationLibrary>();
		
		char[] bufCharArr = new char[] {'b','u','f'};
		char[] outCharArr = new char[] {'o','u','t'};
		
		//定义方法头部public void encode(final java.io.DataOutput buf) throws java.io.IOException
		MethodDeclaration method = new MethodDeclaration(((CompilationUnitDeclaration) typeNode.top().get()).compilationResult);
		setGeneratedBy(method, source);
		method.modifiers = toEclipseModifier(AccessLevel.PUBLIC);
		//无返回值
		method.returnType = TypeReference.baseTypeReference(TypeIds.T_void, 0);
		method.returnType.sourceStart = pS;
		method.returnType.sourceEnd = pE;
		setGeneratedBy(method.returnType, source);
		Annotation overrideAnnotation = makeMarkerAnnotation(TypeConstants.JAVA_LANG_OVERRIDE, source);
		if (getCheckerFrameworkVersion(typeNode).generateSideEffectFree()) {
			method.annotations = new Annotation[] {overrideAnnotation, generateNamedAnnotation(source, CheckerFrameworkVersion.NAME__SIDE_EFFECT_FREE)};
		} else {
			method.annotations = new Annotation[] {overrideAnnotation};
		}
		method.selector = "encode".toCharArray();
		
		//抛出IO异常 java.io.IOException
		QualifiedTypeReference ioException = new QualifiedTypeReference(TypeConstants.JAVA_IO_IOEXCEPTION, new long[] {p, p, p});
		setGeneratedBy(ioException, source);
		method.thrownExceptions = new TypeReference[]{ioException};
		
		method.typeParameters = null;
		method.bits |= Eclipse.ECLIPSE_DO_NOT_TOUCH_FLAG;
		method.bodyStart = method.declarationSourceStart = method.sourceStart = source.sourceStart;
		method.bodyEnd = method.declarationSourceEnd = method.sourceEnd = source.sourceEnd;
		
		///java.io.DataOutput 方法参数
		char[][] dataOutputChart = {TypeConstants.JAVA, TypeConstants.IO, "DataOutput".toCharArray()};
		QualifiedTypeReference dataOutputRef = new QualifiedTypeReference(dataOutputChart, new long[] {p, p, p});
		setGeneratedBy(dataOutputRef, source);
		
		method.arguments = new Argument[] {new Argument(bufCharArr, 0, dataOutputRef, 0)};
		method.arguments[0].sourceStart = pS;
		method.arguments[0].sourceEnd = pE;
		//EclipseHandlerUtil.createRelevantNullableAnnotation(typeNode, method.arguments[0], method, applied);
		setGeneratedBy(method.arguments[0], source);
		
		List<Statement> statements = new ArrayList<Statement>();
		
		TypeDeclaration typeDecl = (TypeDeclaration)typeNode.get();
		
		if(!isDirectDescendantOfObject(typeNode)) {
			//super.decode(buf);
			//String nearest = scanForNearestAnnotation(typeNode, Serial.class.getName());
			if(typeDecl.superclass != null && typeDecl.superclass.toString().endsWith("JRso")) {
				MessageSend callToSuper = new MessageSend();
				callToSuper.sourceStart = pS; callToSuper.sourceEnd = pE;
				setGeneratedBy(callToSuper, source);
				callToSuper.receiver = new SuperReference(pS, pE);
				setGeneratedBy(callToSuper.receiver, source);
				callToSuper.selector = "encode".toCharArray();
				SingleNameReference oRef = new SingleNameReference(bufCharArr, p);
				setGeneratedBy(oRef, source);
				callToSuper.arguments = new Expression[] {oRef};
				statements.add(callToSuper);
			}
		}
		
		//cn.jmicro.api.codec.JDataOutput out = (cn.jmicro.api.codec.JDataOutput)buf;
		{
			///cn.jmicro.api.codec.JDataOutput
			char[][] jdataOutputChart = {{'c','n'}, "jmicro".toCharArray(),"api".toCharArray(),"codec".toCharArray(),"JDataOutput".toCharArray()};
			QualifiedTypeReference jdataOutputRef = new QualifiedTypeReference(jdataOutputChart, new long[] {p, p, p});
			setGeneratedBy(jdataOutputRef, source);
			
			LocalDeclaration other = new LocalDeclaration(outCharArr, pS, pE);
			other.modifiers |= ClassFileConstants.AccFinal;
			setGeneratedBy(other, source);
			other.type = jdataOutputRef;
			setGeneratedBy(other.type, source);
			NameReference oRef = new SingleNameReference(bufCharArr, p);
			setGeneratedBy(oRef, source);
			TypeReference targetType = copyType(jdataOutputRef);
			setGeneratedBy(targetType, source);
			other.initialization = makeCastExpression(oRef, targetType, source);
			statements.add(other);
		}
		
		List<EclipseNode> members = new ArrayList<>();
		for(EclipseNode child : typeNode.down()) {
			if (child.getKind() == Kind.FIELD && !child.isStatic() && !child.isTransient()) {
				members.add(child);
			}
		}
		
		if(members.size() > 1) {
			members.sort(new Comparator<EclipseNode>() {
				@Override 
				public int compare(EclipseNode s1, EclipseNode s2) {
					return s1.getName().compareTo(s2.getName());
				}
			});
		}
		
		int i = 0;
		for (EclipseNode f : members) {
			String writeMethodName = null;
			
			FieldDeclaration fd = (FieldDeclaration)f.get();
			TypeReference fieldTypeRef = fd.type;
			
			TypeReference fType = getFieldType(f, HandlerUtil.FieldAccess.PREFER_FIELD);
			
			boolean isArray = fType instanceof ArrayTypeReference;
			
			if(!isArray && f.isPrimitive()) {
				char[] token = fType.getLastToken();
				if(Arrays.equals(TypeConstants.BOOLEAN, token)) {
					writeMethodName = "writeBoolean";
				}else if(Arrays.equals(TypeConstants.CHAR, token)) {
					writeMethodName = "writeChar";
				}else if(Arrays.equals(TypeConstants.BYTE, token)) {
					writeMethodName = "writeByte";
				}else if(Arrays.equals(TypeConstants.SHORT, token)) {
					writeMethodName = "writeShort";
				}else if(Arrays.equals(TypeConstants.INT, token)) {
					writeMethodName = "writeInt";
				}else if(Arrays.equals(TypeConstants.LONG, token)) {
					writeMethodName = "writeLong";
				}else if(Arrays.equals(TypeConstants.FLOAT, token)) {
					writeMethodName = "writeFloat";
				}else if(Arrays.equals(TypeConstants.DOUBLE, token)) {
					writeMethodName = "writeDouble";
				}else {
					throw new RuntimeException("Not support primitive: " + f.toString());
				}
				
				MessageSend callToSuper = new MessageSend();
				callToSuper.sourceStart = pS; callToSuper.sourceEnd = pE;
				setGeneratedBy(callToSuper, source);
				callToSuper.receiver = new SingleNameReference(outCharArr,p);
				setGeneratedBy(callToSuper.receiver, source);
				callToSuper.selector = writeMethodName.toCharArray();
				
				//this.字段名
				FieldReference fieldInThis = new FieldReference(f.getName().toCharArray(), p);
				setGeneratedBy(fieldInThis, source);
				fieldInThis.receiver = new ThisReference(pS, pE);
				//SingleNameReference oRef = new SingleNameReference(f.getName().toCharArray(), p);
				setGeneratedBy(fieldInThis.receiver, source);
				callToSuper.arguments = new Expression[] {fieldInThis};
				statements.add(callToSuper);

			} else {
				
				//char[] token = fType.getLastToken();
				
				FieldReference fieldInThis0 = new FieldReference(f.getName().toCharArray(), p);
				setGeneratedBy(fieldInThis0, source);
				fieldInThis0.receiver = new ThisReference(pS, pE);
				setGeneratedBy(fieldInThis0.receiver, source);
				
				NullLiteral nullVal = new NullLiteral(pS,pE);
				setGeneratedBy(nullVal, source);
				EqualExpression isNullVal = new EqualExpression(fieldInThis0, nullVal, OperatorIds.EQUAL_EQUAL);
				setGeneratedBy(isNullVal, source);

				FieldReference fieldInThis = new FieldReference(f.getName().toCharArray(), p);
				setGeneratedBy(fieldInThis, source);
				fieldInThis.receiver = new ThisReference(pS, pE);
				setGeneratedBy(fieldInThis.receiver, source);
				if (!isArray && EclipseHandlerUtil.typeMatches(Integer.class, f, fType)) {
					writeVal("writeInt",isNullVal, fieldInThis, IntLiteral.buildIntLiteral(new char[] {'0'}, pS, pE), source,statements);
				} else if (!isArray && EclipseHandlerUtil.typeMatches(String.class, f, fType)) {
					writeVal("writeUTF",isNullVal,  fieldInThis, new StringLiteral(new String("").toCharArray(), pS, pE,0), source, statements);
				}  else if (!isArray && EclipseHandlerUtil.typeMatches(Long.class, f, fType)) {
					writeVal("writeLong", isNullVal, fieldInThis, LongLiteral.buildLongLiteral(new char[] {'0','L'}, pS, pE), source,statements);
				} else if (!isArray && EclipseHandlerUtil.typeMatches(Short.class, f, fType)) {
					writeVal("writeShort",isNullVal,  fieldInThis, IntLiteral.buildIntLiteral(new char[] {'0'}, pS, pE), source,statements);
				}else if (!isArray && EclipseHandlerUtil.typeMatches(Byte.class, f, fType)) {
					writeVal("writeByte",isNullVal, fieldInThis, IntLiteral.buildIntLiteral(new char[] {'0'}, pS, pE), source,statements);
				} else if (!isArray && EclipseHandlerUtil.typeMatches(Double.class, f, fType)) {
					 writeVal("writeDouble",isNullVal, fieldInThis, new DoubleLiteral(new char[] {'0'}, pS, pE), source,statements);
				} else if (!isArray && EclipseHandlerUtil.typeMatches(Float.class, f, fType)) {
					 writeVal("writeFloat", isNullVal, fieldInThis, new FloatLiteral(new char[] {'0'}, pS, pE), source,statements);
				} else if (!isArray && EclipseHandlerUtil.typeMatches(Boolean.class, f, fType)) {
					writeVal("writeBoolean", isNullVal, fieldInThis, new FalseLiteral(pS, pE), source,statements);
				} else if (!isArray && EclipseHandlerUtil.typeMatches(Character.class, f, fType)) {
					writeVal("writeChar", isNullVal, fieldInThis, new CharLiteral(new char[] {' '}, pS, pE), source,statements);
				} else if (!isArray && EclipseHandlerUtil.typeMatches(Date.class, f, fType)) {
					//取得长整数时间
					MessageSend getTime = new MessageSend();
					getTime.sourceStart = pS; 
					getTime.sourceEnd = pE;
					setGeneratedBy(getTime, source);
					getTime.receiver = fieldInThis;
					getTime.selector = "getTime".toCharArray();
					writeVal("writeLong", isNullVal, getTime, LongLiteral.buildLongLiteral(new char[] {'0'}, pS, pE), source,statements);
				}else {
					//System.out.println(cn.jmicro.api.codec.DecoderConstant.PREFIX_TYPE_PROXY);
					/**
					 if (this.tags == null) out.write(cn.jmicro.api.codec.DecoderConstant.PREFIX_TYPE_NULL); 
					 else {
						out.write(cn.jmicro.api.codec.DecoderConstant.PREFIX_TYPE_PROXY);
						cn.jmicro.api.codec.typecoder.ITypeCoder __coder = cn.jmicro.api.codec.TypeCoderFactoryUtils.getIns().getDefaultCoder();
						__coder.encode(buf, this.tags, Set.class, null);
					}
					 */
					
					//cn.jmicro.api.codec.DecoderConstant.PREFIX_TYPE_NULL
					char[][] DecoderConstantRef = {{'c','n'}, "jmicro".toCharArray(),"api".toCharArray(),"codec".toCharArray(),"DecoderConstant".toCharArray()};
					
					FieldReference nullTypePrefix = new FieldReference("PREFIX_TYPE_NULL".toCharArray(), p);
					nullTypePrefix.receiver = generateQualifiedNameRef(source, DecoderConstantRef);
					setGeneratedBy(nullTypePrefix, source);
					
					MessageSend thenWriteVal = new MessageSend();
					thenWriteVal.sourceStart = pS; 
					thenWriteVal.sourceEnd = pE;
					setGeneratedBy(thenWriteVal, source);
					thenWriteVal.receiver = new SingleNameReference(new char[] {'o','u','t'},p);
					setGeneratedBy(thenWriteVal.receiver, source);
					thenWriteVal.selector = "write".toCharArray();
					thenWriteVal.arguments = new Expression[] {nullTypePrefix};
					//cn.jmicro.api.codec.DecoderConstant.PREFIX_TYPE_NULL end
					
					
					//else写值
					if(!isArray) {
						Block inner = new Block(0);
						inner.statements = new Statement[2];
						
						//out.write(cn.jmicro.api.codec.DecoderConstant.PREFIX_TYPE_PROXY);
						FieldReference proxyTypePrefix = new FieldReference("PREFIX_TYPE_PROXY".toCharArray(), p);
						proxyTypePrefix.receiver = generateQualifiedNameRef(source, DecoderConstantRef);
						setGeneratedBy(proxyTypePrefix, source);
						
						MessageSend writeProxyValSt = new MessageSend();
						writeProxyValSt.sourceStart = pS; 
						writeProxyValSt.sourceEnd = pE;
						setGeneratedBy(writeProxyValSt, source);
						writeProxyValSt.receiver = new SingleNameReference(new char[] {'o','u','t'},p);
						setGeneratedBy(thenWriteVal.receiver, source);
						writeProxyValSt.selector = "write".toCharArray();
						writeProxyValSt.arguments = new Expression[] {proxyTypePrefix};
						inner.statements[0] = writeProxyValSt;
						//out.write(cn.jmicro.api.codec.DecoderConstant.PREFIX_TYPE_PROXY); end
						
						//cn.jmicro.api.codec.TypeCoderFactoryUtils.getIns().getDefaultCoder();
						char[][] TypeCoderFactoryUtils = {{'c','n'}, "jmicro".toCharArray(),"api".toCharArray(),"codec".toCharArray(),"TypeCoderFactoryUtils".toCharArray()};
						MessageSend getIns = new MessageSend();
						getIns.sourceStart = pS; 
						getIns.sourceEnd = pE;
						setGeneratedBy(getIns, source);
						getIns.receiver = generateQualifiedNameRef(source, TypeCoderFactoryUtils);
						setGeneratedBy(getIns.receiver, source);
						getIns.selector = "getIns".toCharArray();
						getIns.arguments = null;
						
						MessageSend getDefaultCoder = new MessageSend();
						getDefaultCoder.sourceStart = pS;
						getDefaultCoder.sourceEnd = pE;
						setGeneratedBy(getDefaultCoder, source);
						getDefaultCoder.receiver = getIns;
						setGeneratedBy(getDefaultCoder.receiver, source);
						getDefaultCoder.selector = "getDefaultCoder".toCharArray();
						getDefaultCoder.arguments = null;
						
						//__coder.encode(buf, this.tags, Set.class, null);
						MessageSend encode = new MessageSend();
						encode.sourceStart = pS;
						encode.sourceEnd = pE;
						setGeneratedBy(encode, source);
						encode.receiver = getDefaultCoder;
						setGeneratedBy(encode.receiver, source);
						encode.selector = "encode".toCharArray();
						encode.arguments = new Expression[4];
						encode.arguments[0] = new SingleNameReference(new char[] {'b','u','f'},p);
						setGeneratedBy(encode.arguments[0], source);
						
						FieldReference fieldInThis3 = new FieldReference(f.getName().toCharArray(), p);
						setGeneratedBy(fieldInThis3, source);
						fieldInThis3.receiver = new ThisReference(pS, pE);
						setGeneratedBy(fieldInThis3.receiver, source);
						encode.arguments[1] = fieldInThis3;
						
						
						/*char[][] clazzCharArray = {fType.toString().toCharArray(), "class".toCharArray()};
						encode.arguments[2] = generateQualifiedNameRef(source, clazzCharArray);
						setGeneratedBy(encode.arguments[2], source);*/
						
						encode.arguments[2] = new NullLiteral(fieldInThis.sourceStart, fieldInThis.sourceEnd);
						setGeneratedBy(encode.arguments[2], source);
						
						encode.arguments[3] = new NullLiteral(fieldInThis.sourceStart, fieldInThis.sourceEnd) ;
						setGeneratedBy(encode.arguments[3], source);
						//cn.jmicro.api.codec.TypeCoderFactoryUtils.getIns().getDefaultCoder(); end
						
						String cn = "cn.jmicro.api.codec.ISerializeObject";
						TypeReference serialTypreRef = EclipseHandlerUtil.createTypeReference(cn, source);
						setGeneratedBy(serialTypreRef, source);
						
						// ((ISerializeObject)this.fieldName).encode(buff);
						Block encodeDefBlock = new Block(0);
						setGeneratedBy(encodeDefBlock, source);
						encodeDefBlock.statements = new Statement[2];
						
						String lbname = "b"+i++;
						LocalDeclaration other = new LocalDeclaration(lbname.toCharArray(), pS, pE);
						other.modifiers |= ClassFileConstants.AccFinal;
						setGeneratedBy(other, source);
						other.type = serialTypreRef;
						other.initialization = makeCastExpression(fieldInThis, EclipseHandlerUtil.copyType(serialTypreRef,source), source);
						encodeDefBlock.statements[0] = other;
						
						NameReference oRef = new SingleNameReference(lbname.toCharArray(), p);
						setGeneratedBy(oRef, source);
						
						MessageSend serialEncodeObjCall = new MessageSend();
						serialEncodeObjCall.sourceStart = pS; serialEncodeObjCall.sourceEnd = pE;
						setGeneratedBy(serialEncodeObjCall, source);
						serialEncodeObjCall.receiver = oRef;
						serialEncodeObjCall.selector = "encode".toCharArray();
						SingleNameReference buff = new SingleNameReference(bufCharArr, p);
						setGeneratedBy(buff, source);
						serialEncodeObjCall.arguments = new Expression[] {buff};
						
						encodeDefBlock.statements[1] = serialEncodeObjCall;
						// ((ISerializeObject)this.fieldName).encode(buff); end
						
						//if(this.fileName instanceof ISerializeObject)
						Expression insOf = new InstanceOfExpression(fieldInThis, EclipseHandlerUtil.copyType(serialTypreRef,source));
						
						IfStatement instanceofStatement = new IfStatement(insOf, encodeDefBlock, encode, pS, pE);
						setGeneratedBy(instanceofStatement, source);
						
						inner.statements[1] = instanceofStatement;
						
						IfStatement ifOtherEqualsThis = new IfStatement(isNullVal, thenWriteVal, inner, pS, pE);
						setGeneratedBy(ifOtherEqualsThis, source);
						statements.add(ifOtherEqualsThis);
					} else {

						Block inner = new Block(0);
						inner.statements = new Statement[2];
						
						//out.write(cn.jmicro.api.codec.DecoderConstant.PREFIX_TYPE_PROXY);
						FieldReference proxyTypePrefix = new FieldReference("PREFIX_TYPE_PROXY".toCharArray(), p);
						proxyTypePrefix.receiver = generateQualifiedNameRef(source, DecoderConstantRef);
						setGeneratedBy(proxyTypePrefix, source);
						MessageSend writeProxyValSt = new MessageSend();
						writeProxyValSt.sourceStart = pS; 
						writeProxyValSt.sourceEnd = pE;
						setGeneratedBy(writeProxyValSt, source);
						writeProxyValSt.receiver = new SingleNameReference(new char[] {'o','u','t'},p);
						setGeneratedBy(thenWriteVal.receiver, source);
						writeProxyValSt.selector = "write".toCharArray();
						writeProxyValSt.arguments = new Expression[] {proxyTypePrefix};
						inner.statements[0] = writeProxyValSt;
						//out.write(cn.jmicro.api.codec.DecoderConstant.PREFIX_TYPE_PROXY); end
						
						//cn.jmicro.api.codec.TypeCoderFactoryUtils.getIns().getDefaultCoder();
						char[][] TypeCoderFactoryUtils = {{'c','n'}, "jmicro".toCharArray(),"api".toCharArray(),"codec".toCharArray(),"TypeCoderFactoryUtils".toCharArray()};
						MessageSend getIns = new MessageSend();
						getIns.sourceStart = pS; 
						getIns.sourceEnd = pE;
						setGeneratedBy(getIns, source);
						getIns.receiver = generateQualifiedNameRef(source, TypeCoderFactoryUtils);
						setGeneratedBy(getIns.receiver, source);
						getIns.selector = "getIns".toCharArray();
						getIns.arguments = null;
						
						MessageSend getDefaultCoder = new MessageSend();
						getDefaultCoder.sourceStart = pS;
						getDefaultCoder.sourceEnd = pE;
						setGeneratedBy(getDefaultCoder, source);
						getDefaultCoder.receiver = getIns;
						setGeneratedBy(getDefaultCoder.receiver, source);
						getDefaultCoder.selector = "getDefaultCoder".toCharArray();
						getDefaultCoder.arguments = null;
						
						//__coder.encode(buf, this.tags, Set.class, null);
						MessageSend encode = new MessageSend();
						encode.sourceStart = pS;
						encode.sourceEnd = pE;
						setGeneratedBy(encode, source);
						encode.receiver = getDefaultCoder;
						setGeneratedBy(encode.receiver, source);
						encode.selector = "encode".toCharArray();
						encode.arguments = new Expression[4];
						encode.arguments[0] = new SingleNameReference(new char[] {'b','u','f'},p);
						setGeneratedBy(encode.arguments[0], source);
						
						FieldReference fieldInThis2 = new FieldReference(f.getName().toCharArray(), p);
						setGeneratedBy(fieldInThis2, source);
						fieldInThis2.receiver = new ThisReference(pS, pE);
						setGeneratedBy(fieldInThis2.receiver, source);
						encode.arguments[1] = fieldInThis2;
						
						encode.arguments[2] = new NullLiteral(pS, pE) ;
						encode.arguments[3] = new NullLiteral(pS, pE) ;
						setGeneratedBy(encode.arguments[3], source);
						//cn.jmicro.api.codec.TypeCoderFactoryUtils.getIns().getDefaultCoder(); end
						
						IfStatement ifOtherEqualsThis = new IfStatement(isNullVal, thenWriteVal, encode, pS, pE);
						setGeneratedBy(ifOtherEqualsThis, source);
						statements.add(ifOtherEqualsThis);
						
					}
					
				}
				
			}
		}
		
		method.statements = statements.toArray(new Statement[0]);
		method.traverse(new SetGeneratedByVisitor(errorNode.get()), ((TypeDeclaration)typeNode.get()).scope);
		injectMethod(typeNode, method);
		
	}
	
	private void writeVal(String writeMethodName,EqualExpression isNullVal, Expression fieldInThis,
		Literal devVal, ASTNode source, List<Statement> statements) {
		int pS = source.sourceStart;
		int pE = source.sourceEnd;
		long p = (long) pS << 32 | pE;
		
		//val== null 写默认值
		MessageSend thenWriteVal = new MessageSend();
		thenWriteVal.sourceStart = pS; 
		thenWriteVal.sourceEnd = pE;
		setGeneratedBy(thenWriteVal, source);
		thenWriteVal.receiver = new SingleNameReference(new char[] {'o','u','t'},p);
		setGeneratedBy(thenWriteVal.receiver, source);
		thenWriteVal.selector = writeMethodName.toCharArray();
		thenWriteVal.arguments = new Expression[] {devVal};
		
		//else写值
		MessageSend elseWriteVal = new MessageSend();
		elseWriteVal.sourceStart = pS; 
		elseWriteVal.sourceEnd = pE;
		setGeneratedBy(elseWriteVal, source);
		elseWriteVal.receiver = new SingleNameReference(new char[] {'o','u','t'},p);
		setGeneratedBy(elseWriteVal.receiver, source);
		elseWriteVal.selector = writeMethodName.toCharArray();
		elseWriteVal.arguments = new Expression[] {fieldInThis};
		
		IfStatement ifOtherEqualsThis = new IfStatement(isNullVal, thenWriteVal,elseWriteVal, pS, pE);
		setGeneratedBy(ifOtherEqualsThis, source);
		statements.add(ifOtherEqualsThis);
		
	}
	
}
