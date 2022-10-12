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

import static lombok.core.handlers.HandlerUtil.handleFlagUsage;
import static lombok.eclipse.handlers.EclipseHandlerUtil.*;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.ast.Argument;
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
import org.eclipse.jdt.internal.compiler.ast.IntLiteral;
import org.eclipse.jdt.internal.compiler.ast.Literal;
import org.eclipse.jdt.internal.compiler.ast.LocalDeclaration;
import org.eclipse.jdt.internal.compiler.ast.LongLiteral;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.NameReference;
import org.eclipse.jdt.internal.compiler.ast.NullLiteral;
import org.eclipse.jdt.internal.compiler.ast.OperatorIds;
import org.eclipse.jdt.internal.compiler.ast.ParameterizedQualifiedTypeReference;
import org.eclipse.jdt.internal.compiler.ast.ParameterizedSingleTypeReference;
import org.eclipse.jdt.internal.compiler.ast.QualifiedTypeReference;
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference;
import org.eclipse.jdt.internal.compiler.ast.SingleTypeReference;
import org.eclipse.jdt.internal.compiler.ast.Statement;
import org.eclipse.jdt.internal.compiler.ast.StringLiteral;
import org.eclipse.jdt.internal.compiler.ast.SuperReference;
import org.eclipse.jdt.internal.compiler.ast.ThisReference;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.jdt.internal.compiler.ast.Wildcard;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.lookup.TypeConstants;
import org.eclipse.jdt.internal.compiler.lookup.TypeIds;

import lombok.AccessLevel;
import lombok.ConfigurationKeys;
import lombok.Serial;
import lombok.core.AST.Kind;
import lombok.core.AnnotationValues;
import lombok.core.configuration.CheckerFrameworkVersion;
import lombok.core.configuration.NullAnnotationLibrary;
import lombok.core.handlers.HandlerUtil;
import lombok.eclipse.Eclipse;
import lombok.eclipse.EclipseAnnotationHandler;
import lombok.eclipse.EclipseNode;
import lombok.spi.Provides;

/**
 * Handles the {@code lombok.Getter} annotation for eclipse.
 */
@Provides
public class HandleSerial extends EclipseAnnotationHandler<Serial> {
	
	public void handle(AnnotationValues<Serial> annotation, Annotation ast, EclipseNode annotationNode) {
		handleFlagUsage(annotationNode, ConfigurationKeys.GETTER_FLAG_USAGE, "@Getter");
		
		//List<Annotation> onParam = unboxAndRemoveAnnotationParameter(ast, "", "@Serial(", annotationNode);
		EclipseNode node = annotationNode.up();
		// Serial annotationInstance = annotation.getInstance();
		
		if (node == null) return;
		
		createEncodeMethod(node,annotationNode, ast);
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
			String nearest = scanForNearestAnnotation(typeNode, Serial.class.getName());
			if (nearest != null) {
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
		
		for (EclipseNode f : members) {
			String writeMethodName = null;
			int i = 0;
			FieldDeclaration fd = (FieldDeclaration)f.get();
			TypeReference fieldTypeRef = fd.type;
			
			TypeReference fType = getFieldType(f, HandlerUtil.FieldAccess.PREFER_FIELD);
			char[] token = fType.getLastToken();
			
			if(f.isPrimitive()) {
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
				
				FieldReference fieldInThis = new FieldReference(f.getName().toCharArray(), p);
				setGeneratedBy(fieldInThis, source);
				fieldInThis.receiver = new ThisReference(pS, pE);
				setGeneratedBy(fieldInThis.receiver, source);
				
				String tname = fType.toString();
				if (tname.equals(Integer.class.getName()) || tname.equals(Integer.class.getSimpleName())) {
					writeVal("writeInt", fieldInThis, IntLiteral.buildIntLiteral(new char[] {'0'}, pS, pE), source,statements);
				} else if (tname.equals(Long.class.getName()) || tname.equals(Long.class.getSimpleName())) {
					writeVal( "writeLong", fieldInThis, LongLiteral.buildLongLiteral(new char[] {'0'}, pS, pE), source,statements);
				} else if (tname.equals(String.class.getName())) {
					writeVal( "writeUTF", fieldInThis, new StringLiteral(new char[] {'0'}, pS, pE,0), source, statements);
				} else if (tname.equals(Byte.class.getName()) || tname.equals(Byte.class.getSimpleName())) {
					writeVal("writeByte",fieldInThis, IntLiteral.buildIntLiteral(new char[] {'0'}, pS, pE), source,statements);
				} else if (tname.equals(Double.class.getName()) || tname.equals(Double.class.getSimpleName())) {
					 writeVal("writeDouble",fieldInThis, new DoubleLiteral(new char[] {'0'}, pS, pE), source,statements);
				} else if (tname.equals(Float.class.getName()) || tname.equals(Float.class.getSimpleName())) {
					 writeVal("writeFloat", fieldInThis, new FloatLiteral(new char[] {'0'}, pS, pE), source,statements);
				} else if (tname.equals(Boolean.class.getName()) || tname.equals(Boolean.class.getSimpleName())) {
					writeVal("writeBoolean", fieldInThis, new FalseLiteral(pS, pE), source,statements);
				} else if (tname.equals(Character.class.getName()) || tname.equals(Character.class.getSimpleName())) {
					writeVal("writeChar", fieldInThis, new CharLiteral(new char[] {' '}, pS, pE), source,statements);
				} else if (tname.equals(Short.class.getName()) || tname.equals(Short.class.getSimpleName())) {
					writeVal("writeShort", fieldInThis, IntLiteral.buildIntLiteral(new char[] {'0'}, pS, pE), source,statements);
				}
				
			}
		}
		
		method.statements = statements.toArray(new Statement[0]);
		method.traverse(new SetGeneratedByVisitor(errorNode.get()), ((TypeDeclaration)typeNode.get()).scope);
		injectMethod(typeNode, method);
		
	}
	
	private void writeVal(String writeMethodName, FieldReference fieldInThis,
		Literal devVal, ASTNode source, List<Statement> statements) {
		int pS = source.sourceStart;
		int pE = source.sourceEnd;
		long p = (long) pS << 32 | pE;
		
		NullLiteral nullVal = new NullLiteral(fieldInThis.sourceStart,fieldInThis.sourceEnd);
		setGeneratedBy(nullVal, source);
		EqualExpression otherEqualsThis = new EqualExpression(fieldInThis, nullVal, OperatorIds.EQUAL_EQUAL);
		setGeneratedBy(otherEqualsThis, source);
		
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
		
		IfStatement ifOtherEqualsThis = new IfStatement(otherEqualsThis, thenWriteVal,elseWriteVal, pS, pE);
		setGeneratedBy(ifOtherEqualsThis, source);
		statements.add(ifOtherEqualsThis);
		
	}
	
	/**
	 * @param type Type to 'copy' into a typeref
	 * @param p position
	 * @param addWildcards If false, all generics are cut off. If true, replaces all genericparams with a ?.
	 * @return
	 */
	public TypeReference createTypeReference(EclipseNode type, long p, ASTNode source, boolean addWildcards) {
		int pS = source.sourceStart; int pE = source.sourceEnd;
		List<String> list = new ArrayList<String>();
		List<Integer> genericsCount = addWildcards ? new ArrayList<Integer>() : null;
		
		list.add(type.getName());
		if (addWildcards) genericsCount.add(arraySizeOf(((TypeDeclaration) type.get()).typeParameters));
		boolean staticContext = (((TypeDeclaration) type.get()).modifiers & ClassFileConstants.AccStatic) != 0;
		EclipseNode tNode = type.up();
		
		while (tNode != null && tNode.getKind() == Kind.TYPE) {
			TypeDeclaration td = (TypeDeclaration) tNode.get();
			if (td.name == null || td.name.length == 0) break;
			list.add(tNode.getName());
			if (!staticContext && tNode.getKind() == Kind.TYPE && (td.modifiers & ClassFileConstants.AccInterface) != 0) staticContext = true;
			if (addWildcards) genericsCount.add(staticContext ? 0 : arraySizeOf(td.typeParameters));
			if (!staticContext) staticContext = (td.modifiers & Modifier.STATIC) != 0;
			tNode = tNode.up();
		}
		Collections.reverse(list);
		if (addWildcards) Collections.reverse(genericsCount);
		
		if (list.size() == 1) {
			if (!addWildcards || genericsCount.get(0) == 0) {
				return new SingleTypeReference(list.get(0).toCharArray(), p);
			} else {
				return new ParameterizedSingleTypeReference(list.get(0).toCharArray(), wildcardify(pS, pE, source, genericsCount.get(0)), 0, p);
			}
		}
		
		if (addWildcards) {
			addWildcards = false;
			for (int i : genericsCount) if (i > 0) addWildcards = true;
		}
		
		long[] ps = new long[list.size()];
		char[][] tokens = new char[list.size()][];
		for (int i = 0; i < list.size(); i++) {
			ps[i] = p;
			tokens[i] = list.get(i).toCharArray();
		}
		
		if (!addWildcards) return new QualifiedTypeReference(tokens, ps);
		TypeReference[][] typeArgs2 = new TypeReference[tokens.length][];
		for (int i = 0; i < tokens.length; i++) typeArgs2[i] = wildcardify(pS, pE, source, genericsCount.get(i));
		return new ParameterizedQualifiedTypeReference(tokens, typeArgs2, 0, ps);
	}
	
	private TypeReference[] wildcardify(int pS, int pE, ASTNode source, int count) {
		if (count == 0) return null;
		TypeReference[] typeArgs = new TypeReference[count];
		for (int i = 0; i < count; i++) {
			typeArgs[i] = new Wildcard(Wildcard.UNBOUND);
			typeArgs[i].sourceStart = pS; typeArgs[i].sourceEnd = pE;
			setGeneratedBy(typeArgs[i], source);
		}
		
		return typeArgs;
	}
	
	private int arraySizeOf(Object[] arr) {
		return arr == null ? 0 : arr.length;
	}
}
