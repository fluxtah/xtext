/**
 * Copyright (c) 2013 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.xtend.core.macro;

import com.google.common.base.Objects;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtend.core.macro.ConstantExpressionEvaluationException;
import org.eclipse.xtend.core.macro.ConstantOperators;
import org.eclipse.xtend.core.macro.Context;
import org.eclipse.xtend.core.macro.ProcessorInstanceForJvmTypeProvider;
import org.eclipse.xtend.core.macro.StackedConstantExpressionEvaluationException;
import org.eclipse.xtend.core.macro.UnresolvableFeatureException;
import org.eclipse.xtext.common.types.JvmAnnotationType;
import org.eclipse.xtext.common.types.JvmArrayType;
import org.eclipse.xtext.common.types.JvmComponentType;
import org.eclipse.xtext.common.types.JvmDeclaredType;
import org.eclipse.xtext.common.types.JvmEnumerationLiteral;
import org.eclipse.xtext.common.types.JvmEnumerationType;
import org.eclipse.xtext.common.types.JvmFeature;
import org.eclipse.xtext.common.types.JvmField;
import org.eclipse.xtext.common.types.JvmGenericArrayTypeReference;
import org.eclipse.xtext.common.types.JvmGenericType;
import org.eclipse.xtext.common.types.JvmIdentifiableElement;
import org.eclipse.xtext.common.types.JvmMember;
import org.eclipse.xtext.common.types.JvmParameterizedTypeReference;
import org.eclipse.xtext.common.types.JvmType;
import org.eclipse.xtext.common.types.JvmTypeReference;
import org.eclipse.xtext.common.types.TypesFactory;
import org.eclipse.xtext.common.types.TypesPackage;
import org.eclipse.xtext.common.types.access.TypeResource;
import org.eclipse.xtext.common.types.access.impl.ClassFinder;
import org.eclipse.xtext.naming.IQualifiedNameConverter;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.IScopeProvider;
import org.eclipse.xtext.xbase.XAbstractFeatureCall;
import org.eclipse.xtext.xbase.XBinaryOperation;
import org.eclipse.xtext.xbase.XBooleanLiteral;
import org.eclipse.xtext.xbase.XCastedExpression;
import org.eclipse.xtext.xbase.XExpression;
import org.eclipse.xtext.xbase.XFeatureCall;
import org.eclipse.xtext.xbase.XListLiteral;
import org.eclipse.xtext.xbase.XMemberFeatureCall;
import org.eclipse.xtext.xbase.XNumberLiteral;
import org.eclipse.xtext.xbase.XStringLiteral;
import org.eclipse.xtext.xbase.XTypeLiteral;
import org.eclipse.xtext.xbase.XUnaryOperation;
import org.eclipse.xtext.xbase.annotations.xAnnotations.XAnnotation;
import org.eclipse.xtext.xbase.imports.IImportsConfiguration;
import org.eclipse.xtext.xbase.jvmmodel.ILogicalContainerProvider;
import org.eclipse.xtext.xbase.lib.CollectionLiterals;
import org.eclipse.xtext.xbase.lib.Conversions;
import org.eclipse.xtext.xbase.lib.Exceptions;
import org.eclipse.xtext.xbase.lib.ExclusiveRange;
import org.eclipse.xtext.xbase.lib.Extension;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
import org.eclipse.xtext.xbase.lib.ListExtensions;
import org.eclipse.xtext.xbase.lib.ObjectExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import org.eclipse.xtext.xbase.typesystem.computation.NumberLiterals;
import org.eclipse.xtext.xtype.XComputedTypeReference;
import org.eclipse.xtext.xtype.XImportDeclaration;
import org.eclipse.xtext.xtype.XImportSection;

/**
 * An interpreter for evaluating constant expressions in annotation values.
 * 
 * @author Sven Efftinge
 */
@SuppressWarnings("all")
public class ConstantExpressionsInterpreter {
  @Inject
  private ILogicalContainerProvider containerProvider;
  
  @Inject
  private ProcessorInstanceForJvmTypeProvider classLoaderProvider;
  
  @Inject
  @Extension
  private NumberLiterals numberLiterals;
  
  @Inject
  private ConstantOperators constantOperators;
  
  @Inject
  private IScopeProvider scopeProvider;
  
  @Inject
  private IImportsConfiguration importSectionLocator;
  
  @Inject
  private IQualifiedNameConverter qualifiedNameConverter;
  
  public Object evaluate(final XExpression expression, final JvmTypeReference expectedType) {
    final ClassLoader classLoader = this.classLoaderProvider.getClassLoader(expression);
    final Map<String,JvmIdentifiableElement> visibleFeatures = this.findVisibleFeatures(expression);
    JvmTypeReference _xifexpression = null;
    if ((expectedType instanceof XComputedTypeReference)) {
      _xifexpression = null;
    } else {
      _xifexpression = expectedType;
    }
    ClassFinder _classFinder = new ClassFinder(classLoader);
    LinkedHashSet<XExpression> _newLinkedHashSet = CollectionLiterals.<XExpression>newLinkedHashSet();
    Context _context = new Context(_xifexpression, _classFinder, visibleFeatures, _newLinkedHashSet);
    final Object result = this.internalEvaluate(expression, _context);
    return result;
  }
  
  /**
   * looks up the static final fields which are accessible in unqualified form for the given expression.
   * That essentially includes static imports and the fields declared in the containing types
   */
  protected Map<String,JvmIdentifiableElement> findVisibleFeatures(final XExpression expression) {
    final HashMap<String,JvmIdentifiableElement> result = CollectionLiterals.<String, JvmIdentifiableElement>newHashMap();
    Resource _eResource = expression.eResource();
    final XImportSection section = this.importSectionLocator.getImportSection(((XtextResource) _eResource));
    boolean _notEquals = (!Objects.equal(section, null));
    if (_notEquals) {
      EList<XImportDeclaration> _importDeclarations = section.getImportDeclarations();
      for (final XImportDeclaration imp : _importDeclarations) {
        boolean _isStatic = imp.isStatic();
        if (_isStatic) {
          String _importedTypeName = imp.getImportedTypeName();
          final JvmType type = this.findTypeByName(imp, _importedTypeName);
          boolean _matched = false;
          if (!_matched) {
            if (type instanceof JvmGenericType) {
              _matched=true;
              Iterable<JvmFeature> _allFeatures = ((JvmGenericType)type).getAllFeatures();
              Iterable<JvmField> _filter = Iterables.<JvmField>filter(_allFeatures, JvmField.class);
              final Function1<JvmField,Boolean> _function = new Function1<JvmField,Boolean>() {
                public Boolean apply(final JvmField it) {
                  boolean _and = false;
                  boolean _isStatic = it.isStatic();
                  if (!_isStatic) {
                    _and = false;
                  } else {
                    boolean _isFinal = it.isFinal();
                    _and = (_isStatic && _isFinal);
                  }
                  return Boolean.valueOf(_and);
                }
              };
              Iterable<JvmField> _filter_1 = IterableExtensions.<JvmField>filter(_filter, _function);
              for (final JvmField feature : _filter_1) {
                String _simpleName = feature.getSimpleName();
                result.put(_simpleName, feature);
              }
            }
          }
          if (!_matched) {
            if (type instanceof JvmEnumerationType) {
              _matched=true;
              EList<JvmEnumerationLiteral> _literals = ((JvmEnumerationType)type).getLiterals();
              for (final JvmEnumerationLiteral feature : _literals) {
                String _simpleName = feature.getSimpleName();
                result.put(_simpleName, feature);
              }
            }
          }
        }
      }
    }
    JvmDeclaredType _switchResult_1 = null;
    JvmIdentifiableElement _nearestLogicalContainer = this.containerProvider.getNearestLogicalContainer(expression);
    final JvmIdentifiableElement cont = _nearestLogicalContainer;
    boolean _matched_1 = false;
    if (!_matched_1) {
      if (cont instanceof JvmGenericType) {
        _matched_1=true;
        _switchResult_1 = ((JvmGenericType)cont);
      }
    }
    if (!_matched_1) {
      if (cont instanceof JvmMember) {
        _matched_1=true;
        JvmDeclaredType _declaringType = ((JvmMember)cont).getDeclaringType();
        _switchResult_1 = _declaringType;
      }
    }
    JvmDeclaredType container = _switchResult_1;
    boolean _notEquals_1 = (!Objects.equal(container, null));
    boolean _while = _notEquals_1;
    while (_while) {
      {
        Iterable<JvmFeature> _allFeatures = container.getAllFeatures();
        Iterable<JvmField> _filter = Iterables.<JvmField>filter(_allFeatures, JvmField.class);
        final Function1<JvmField,Boolean> _function = new Function1<JvmField,Boolean>() {
          public Boolean apply(final JvmField it) {
            boolean _and = false;
            boolean _isStatic = it.isStatic();
            if (!_isStatic) {
              _and = false;
            } else {
              boolean _isFinal = it.isFinal();
              _and = (_isStatic && _isFinal);
            }
            return Boolean.valueOf(_and);
          }
        };
        Iterable<JvmField> _filter_1 = IterableExtensions.<JvmField>filter(_filter, _function);
        for (final JvmField feature : _filter_1) {
          String _simpleName = feature.getSimpleName();
          result.put(_simpleName, feature);
        }
        JvmDeclaredType _declaringType = container.getDeclaringType();
        container = _declaringType;
      }
      boolean _notEquals_2 = (!Objects.equal(container, null));
      _while = _notEquals_2;
    }
    return result;
  }
  
  protected JvmType findTypeByName(final EObject context, final String qualifiedName) {
    final IScope scope = this.scopeProvider.getScope(context, TypesPackage.Literals.JVM_PARAMETERIZED_TYPE_REFERENCE__TYPE);
    final QualifiedName qn = this.qualifiedNameConverter.toQualifiedName(qualifiedName);
    IEObjectDescription _singleElement = scope.getSingleElement(qn);
    EObject _eObjectOrProxy = null;
    if (_singleElement!=null) {
      _eObjectOrProxy=_singleElement.getEObjectOrProxy();
    }
    return ((JvmType) _eObjectOrProxy);
  }
  
  protected Object _internalEvaluate(final XExpression expression, final Context ctx) {
    String _text = this.toText(expression);
    String _plus = ("Not a constant expression : \'" + _text);
    String _plus_1 = (_plus + "\'");
    ConstantExpressionEvaluationException _constantExpressionEvaluationException = new ConstantExpressionEvaluationException(_plus_1, expression);
    throw _constantExpressionEvaluationException;
  }
  
  protected Object _internalEvaluate(final XCastedExpression expression, final Context ctx) {
    XExpression _target = expression.getTarget();
    Object _internalEvaluate = this.internalEvaluate(_target, ctx);
    return _internalEvaluate;
  }
  
  protected Object _internalEvaluate(final XStringLiteral it, final Context ctx) {
    String _value = it.getValue();
    return _value;
  }
  
  protected Object _internalEvaluate(final XBooleanLiteral it, final Context ctx) {
    boolean _isIsTrue = it.isIsTrue();
    return Boolean.valueOf(_isIsTrue);
  }
  
  protected Object _internalEvaluate(final XNumberLiteral it, final Context ctx) {
    try {
      Number _xblockexpression = null;
      {
        Class<? extends Number> _xifexpression = null;
        JvmTypeReference _expectedType = ctx.getExpectedType();
        boolean _equals = Objects.equal(_expectedType, null);
        if (_equals) {
          Class<? extends Number> _javaType = this.numberLiterals.getJavaType(it);
          _xifexpression = _javaType;
        } else {
          JvmTypeReference _expectedType_1 = ctx.getExpectedType();
          JvmType _type = _expectedType_1.getType();
          ClassFinder _classFinder = ctx.getClassFinder();
          Class<? extends Object> _javaType_1 = this.getJavaType(_type, _classFinder);
          _xifexpression = ((Class<? extends Number>) _javaType_1);
        }
        final Class<? extends Number> type = _xifexpression;
        Number _numberValue = this.numberLiterals.numberValue(it, type);
        _xblockexpression = (_numberValue);
      }
      return _xblockexpression;
    } catch (Throwable _e) {
      throw Exceptions.sneakyThrow(_e);
    }
  }
  
  protected Object _internalEvaluate(final XTypeLiteral it, final Context ctx) {
    JvmType _type = it.getType();
    EList<String> _arrayDimensions = it.getArrayDimensions();
    int _size = _arrayDimensions.size();
    JvmTypeReference _typeReference = this.toTypeReference(_type, _size);
    return _typeReference;
  }
  
  protected Object _internalEvaluate(final XAnnotation literal, final Context ctx) {
    return literal;
  }
  
  protected Object _internalEvaluate(final XListLiteral it, final Context ctx) {
    try {
      JvmTypeReference _switchResult = null;
      JvmTypeReference _expectedType = ctx.getExpectedType();
      final JvmTypeReference exp = _expectedType;
      boolean _matched = false;
      if (!_matched) {
        if (exp instanceof JvmGenericArrayTypeReference) {
          _matched=true;
          JvmTypeReference _componentType = ((JvmGenericArrayTypeReference)exp).getComponentType();
          _switchResult = _componentType;
        }
      }
      final JvmTypeReference expectedComponentType = _switchResult;
      EList<XExpression> _elements = it.getElements();
      final Function1<XExpression,Object> _function = new Function1<XExpression,Object>() {
        public Object apply(final XExpression it) {
          Context _cloneWithExpectation = ctx.cloneWithExpectation(expectedComponentType);
          Object _internalEvaluate = ConstantExpressionsInterpreter.this.internalEvaluate(it, _cloneWithExpectation);
          return _internalEvaluate;
        }
      };
      final List<Object> elements = ListExtensions.<XExpression, Object>map(_elements, _function);
      Class<? extends Object> _xifexpression = null;
      boolean _notEquals = (!Objects.equal(expectedComponentType, null));
      if (_notEquals) {
        JvmType _type = expectedComponentType.getType();
        ClassFinder _classFinder = ctx.getClassFinder();
        Class<? extends Object> _javaType = this.getJavaType(_type, _classFinder);
        _xifexpression = _javaType;
      } else {
        Class<? extends Object> _xifexpression_1 = null;
        boolean _isEmpty = elements.isEmpty();
        boolean _not = (!_isEmpty);
        if (_not) {
          Class<? extends Object> _switchResult_1 = null;
          Object _head = IterableExtensions.<Object>head(elements);
          Class<? extends Object> _class = _head.getClass();
          final Class<? extends Object> cl = _class;
          boolean _matched_1 = false;
          if (!_matched_1) {
            if (Objects.equal(cl,Integer.class)) {
              _matched_1=true;
              _switchResult_1 = Integer.TYPE;
            }
          }
          if (!_matched_1) {
            if (Objects.equal(cl,Long.class)) {
              _matched_1=true;
              _switchResult_1 = Long.TYPE;
            }
          }
          if (!_matched_1) {
            if (Objects.equal(cl,Short.class)) {
              _matched_1=true;
              _switchResult_1 = Short.TYPE;
            }
          }
          if (!_matched_1) {
            if (Objects.equal(cl,Boolean.class)) {
              _matched_1=true;
              _switchResult_1 = Boolean.TYPE;
            }
          }
          if (!_matched_1) {
            if (Objects.equal(cl,Double.class)) {
              _matched_1=true;
              _switchResult_1 = Double.TYPE;
            }
          }
          if (!_matched_1) {
            if (Objects.equal(cl,Byte.class)) {
              _matched_1=true;
              _switchResult_1 = Byte.TYPE;
            }
          }
          if (!_matched_1) {
            if (Objects.equal(cl,Float.class)) {
              _matched_1=true;
              _switchResult_1 = Float.TYPE;
            }
          }
          if (!_matched_1) {
            _switchResult_1 = cl;
          }
          _xifexpression_1 = _switchResult_1;
        } else {
          _xifexpression_1 = Object.class;
        }
        _xifexpression = _xifexpression_1;
      }
      final Class<?> componentType = _xifexpression;
      return Conversions.unwrapArray(elements, componentType);
    } catch (Throwable _e) {
      throw Exceptions.sneakyThrow(_e);
    }
  }
  
  protected Object _internalEvaluate(final XFeatureCall it, final Context ctx) {
    final String featureName = it.getConcreteSyntaxFeatureName();
    Map<String,JvmIdentifiableElement> _visibleFeatures = ctx.getVisibleFeatures();
    boolean _containsKey = _visibleFeatures.containsKey(featureName);
    if (_containsKey) {
      Object _switchResult = null;
      Map<String,JvmIdentifiableElement> _visibleFeatures_1 = ctx.getVisibleFeatures();
      JvmIdentifiableElement _get = _visibleFeatures_1.get(featureName);
      final JvmIdentifiableElement feature = _get;
      boolean _matched = false;
      if (!_matched) {
        if (feature instanceof JvmEnumerationLiteral) {
          _matched=true;
          _switchResult = ((JvmEnumerationLiteral)feature);
        }
      }
      if (!_matched) {
        if (feature instanceof JvmField) {
          _matched=true;
          Object _evaluateField = this.evaluateField(it, ((JvmField)feature), ctx);
          _switchResult = _evaluateField;
        }
      }
      return _switchResult;
    }
    final JvmType type = this.findTypeByName(it, featureName);
    boolean _notEquals = (!Objects.equal(type, null));
    if (_notEquals) {
      return this.toTypeReference(type, 0);
    }
    UnresolvableFeatureException _unresolvableFeatureException = new UnresolvableFeatureException(("Couldn\'t resolve feature " + featureName), it);
    throw _unresolvableFeatureException;
  }
  
  protected Object _internalEvaluate(final XMemberFeatureCall it, final Context ctx) {
    final String featureName = it.getConcreteSyntaxFeatureName();
    try {
      XExpression _memberCallTarget = it.getMemberCallTarget();
      final Object receiver = this.internalEvaluate(_memberCallTarget, ctx);
      boolean _matched = false;
      if (!_matched) {
        if (receiver instanceof JvmTypeReference) {
          _matched=true;
          JvmType _type = ((JvmTypeReference)receiver).getType();
          final JvmType type = _type;
          boolean _matched_1 = false;
          if (!_matched_1) {
            if (type instanceof JvmEnumerationType) {
              _matched_1=true;
              EList<JvmEnumerationLiteral> _literals = ((JvmEnumerationType)type).getLiterals();
              final Function1<JvmEnumerationLiteral,Boolean> _function = new Function1<JvmEnumerationLiteral,Boolean>() {
                public Boolean apply(final JvmEnumerationLiteral it) {
                  String _simpleName = it.getSimpleName();
                  boolean _equals = Objects.equal(_simpleName, featureName);
                  return Boolean.valueOf(_equals);
                }
              };
              final JvmEnumerationLiteral enumValue = IterableExtensions.<JvmEnumerationLiteral>findFirst(_literals, _function);
              boolean _equals = Objects.equal(enumValue, null);
              if (_equals) {
                String _simpleName = ((JvmTypeReference)receiver).getSimpleName();
                String _plus = ((("Couldn\'t find enum value " + featureName) + " on enum ") + _simpleName);
                ConstantExpressionEvaluationException _constantExpressionEvaluationException = new ConstantExpressionEvaluationException(_plus, it);
                throw _constantExpressionEvaluationException;
              }
              return enumValue;
            }
          }
          if (!_matched_1) {
            if (type instanceof JvmGenericType) {
              _matched_1=true;
              Iterable<JvmFeature> _allFeatures = ((JvmGenericType)type).getAllFeatures();
              Iterable<JvmField> _filter = Iterables.<JvmField>filter(_allFeatures, JvmField.class);
              final Function1<JvmField,Boolean> _function = new Function1<JvmField,Boolean>() {
                public Boolean apply(final JvmField it) {
                  String _simpleName = it.getSimpleName();
                  boolean _equals = Objects.equal(_simpleName, featureName);
                  return Boolean.valueOf(_equals);
                }
              };
              final JvmField field = IterableExtensions.<JvmField>findFirst(_filter, _function);
              boolean _equals = Objects.equal(field, null);
              if (_equals) {
                String _simpleName = ((JvmTypeReference)receiver).getSimpleName();
                String _plus = ((("Couldn\'t find field " + featureName) + " on type ") + _simpleName);
                ConstantExpressionEvaluationException _constantExpressionEvaluationException = new ConstantExpressionEvaluationException(_plus, it);
                throw _constantExpressionEvaluationException;
              }
              return this.evaluateField(it, field, ctx);
            }
          }
        }
      }
      UnresolvableFeatureException _unresolvableFeatureException = new UnresolvableFeatureException(((("Unresolvable feature " + featureName) + " on ") + receiver), it);
      throw _unresolvableFeatureException;
    } catch (final Throwable _t) {
      if (_t instanceof UnresolvableFeatureException) {
        final UnresolvableFeatureException e = (UnresolvableFeatureException)_t;
        final String typeName = this.getFullName(it);
        final JvmType type = this.findTypeByName(it, typeName);
        boolean _notEquals = (!Objects.equal(type, null));
        if (_notEquals) {
          return this.toTypeReference(type, 0);
        } else {
          UnresolvableFeatureException _unresolvableFeatureException_1 = new UnresolvableFeatureException(("Unresolvable type " + typeName), it);
          throw _unresolvableFeatureException_1;
        }
      } else {
        throw Exceptions.sneakyThrow(_t);
      }
    }
  }
  
  protected String _getFullName(final XExpression call) {
    String _text = this.toText(call);
    String _plus = ("The expression \'" + _text);
    String _plus_1 = (_plus + "\' cannot be used as a receiver within a constant expression.");
    ConstantExpressionEvaluationException _constantExpressionEvaluationException = new ConstantExpressionEvaluationException(_plus_1);
    throw _constantExpressionEvaluationException;
  }
  
  protected String _getFullName(final XMemberFeatureCall call) {
    XExpression _memberCallTarget = call.getMemberCallTarget();
    final String prefix = this.getFullName(_memberCallTarget);
    String _concreteSyntaxFeatureName = call.getConcreteSyntaxFeatureName();
    return ((prefix + ".") + _concreteSyntaxFeatureName);
  }
  
  protected String _getFullName(final XFeatureCall call) {
    String _concreteSyntaxFeatureName = call.getConcreteSyntaxFeatureName();
    return _concreteSyntaxFeatureName;
  }
  
  protected Object evaluateField(final XAbstractFeatureCall call, final JvmField field, final Context context) {
    try {
      Resource _eResource = field.eResource();
      if ((_eResource instanceof TypeResource)) {
        JvmDeclaredType _declaringType = field.getDeclaringType();
        ClassFinder _classFinder = context.getClassFinder();
        final Class<? extends Object> clazz = this.getJavaType(_declaringType, _classFinder);
        String _simpleName = field.getSimpleName();
        Field _field = clazz.getField(_simpleName);
        Object _get = null;
        if (_field!=null) {
          _get=_field.get(null);
        }
        return _get;
      }
      final XExpression expression = this.containerProvider.getAssociatedExpression(field);
      Set<XExpression> _alreadyEvaluating = context.getAlreadyEvaluating();
      boolean _contains = _alreadyEvaluating.contains(expression);
      if (_contains) {
        ConstantExpressionEvaluationException _constantExpressionEvaluationException = new ConstantExpressionEvaluationException("Endless recursive evaluation detected.");
        throw _constantExpressionEvaluationException;
      }
      try {
        final Map<String,JvmIdentifiableElement> visibleFeatures = this.findVisibleFeatures(expression);
        Set<XExpression> _alreadyEvaluating_1 = context.getAlreadyEvaluating();
        LinkedHashSet<XExpression> _linkedHashSet = new LinkedHashSet<XExpression>(_alreadyEvaluating_1);
        final LinkedHashSet<XExpression> set = _linkedHashSet;
        set.add(expression);
        JvmTypeReference _type = field.getType();
        ClassFinder _classFinder_1 = context.getClassFinder();
        Context _context = new Context(_type, _classFinder_1, visibleFeatures, set);
        final Context ctx = _context;
        return this.internalEvaluate(expression, ctx);
      } catch (final Throwable _t) {
        if (_t instanceof ConstantExpressionEvaluationException) {
          final ConstantExpressionEvaluationException e = (ConstantExpressionEvaluationException)_t;
          StackedConstantExpressionEvaluationException _stackedConstantExpressionEvaluationException = new StackedConstantExpressionEvaluationException(call, field, e);
          throw _stackedConstantExpressionEvaluationException;
        } else {
          throw Exceptions.sneakyThrow(_t);
        }
      }
    } catch (Throwable _e) {
      throw Exceptions.sneakyThrow(_e);
    }
  }
  
  protected Object _internalEvaluate(final XBinaryOperation it, final Context ctx) {
    Object _xblockexpression = null;
    {
      XExpression _leftOperand = it.getLeftOperand();
      final Object left = this.internalEvaluate(_leftOperand, ctx);
      XExpression _rightOperand = it.getRightOperand();
      final Object right = this.internalEvaluate(_rightOperand, ctx);
      final String op = it.getConcreteSyntaxFeatureName();
      Object _switchResult = null;
      boolean _matched = false;
      if (!_matched) {
        if (Objects.equal(op,"+")) {
          _matched=true;
          Object _plus = this.constantOperators.plus(left, right);
          _switchResult = _plus;
        }
      }
      if (!_matched) {
        if (Objects.equal(op,"-")) {
          _matched=true;
          Object _minus = this.constantOperators.minus(left, right);
          _switchResult = _minus;
        }
      }
      if (!_matched) {
        if (Objects.equal(op,"*")) {
          _matched=true;
          Object _multiply = this.constantOperators.multiply(left, right);
          _switchResult = _multiply;
        }
      }
      if (!_matched) {
        if (Objects.equal(op,"/")) {
          _matched=true;
          Object _divide = this.constantOperators.divide(left, right);
          _switchResult = _divide;
        }
      }
      if (!_matched) {
        if (Objects.equal(op,"%")) {
          _matched=true;
          Object _modulo = this.constantOperators.modulo(left, right);
          _switchResult = _modulo;
        }
      }
      if (!_matched) {
        if (Objects.equal(op,"<")) {
          _matched=true;
          boolean _lessThan = this.constantOperators.lessThan(left, right);
          _switchResult = Boolean.valueOf(_lessThan);
        }
      }
      if (!_matched) {
        if (Objects.equal(op,">")) {
          _matched=true;
          boolean _greaterThan = this.constantOperators.greaterThan(left, right);
          _switchResult = Boolean.valueOf(_greaterThan);
        }
      }
      if (!_matched) {
        if (Objects.equal(op,"<=")) {
          _matched=true;
          boolean _lessEquals = this.constantOperators.lessEquals(left, right);
          _switchResult = Boolean.valueOf(_lessEquals);
        }
      }
      if (!_matched) {
        if (Objects.equal(op,">=")) {
          _matched=true;
          boolean _greaterEquals = this.constantOperators.greaterEquals(left, right);
          _switchResult = Boolean.valueOf(_greaterEquals);
        }
      }
      if (!_matched) {
        if (Objects.equal(op,"===")) {
          _matched=true;
          boolean _same = this.constantOperators.same(left, right);
          _switchResult = Boolean.valueOf(_same);
        }
      }
      if (!_matched) {
        if (Objects.equal(op,"!==")) {
          _matched=true;
          boolean _notSame = this.constantOperators.notSame(left, right);
          _switchResult = Boolean.valueOf(_notSame);
        }
      }
      if (!_matched) {
        if (Objects.equal(op,"==")) {
          _matched=true;
          ConstantExpressionEvaluationException _constantExpressionEvaluationException = new ConstantExpressionEvaluationException("Please use the identity comparison operator \'===\' in constant expressions.");
          throw _constantExpressionEvaluationException;
        }
      }
      if (!_matched) {
        if (Objects.equal(op,"!=")) {
          _matched=true;
          ConstantExpressionEvaluationException _constantExpressionEvaluationException_1 = new ConstantExpressionEvaluationException("Please use the identity comparison operator \'!==\' in constant expressions.");
          throw _constantExpressionEvaluationException_1;
        }
      }
      if (!_matched) {
        ConstantExpressionEvaluationException _constantExpressionEvaluationException_2 = new ConstantExpressionEvaluationException(((((("Couldn\'t evaluate binary operator \'" + op) + "\' on values ") + left) + " and ") + right));
        throw _constantExpressionEvaluationException_2;
      }
      _xblockexpression = (_switchResult);
    }
    return _xblockexpression;
  }
  
  protected Object _internalEvaluate(final XUnaryOperation it, final Context ctx) {
    Object _xblockexpression = null;
    {
      XExpression _operand = it.getOperand();
      final Object value = this.internalEvaluate(_operand, ctx);
      final String op = it.getConcreteSyntaxFeatureName();
      Object _switchResult = null;
      boolean _matched = false;
      if (!_matched) {
        if (Objects.equal(op,"-")) {
          _matched=true;
          Object _minus = this.constantOperators.minus(value);
          _switchResult = _minus;
        }
      }
      if (!_matched) {
        boolean _and = false;
        boolean _equals = Objects.equal(op, "!");
        if (!_equals) {
          _and = false;
        } else {
          _and = (_equals && (value instanceof Boolean));
        }
        if (_and) {
          _matched=true;
          _switchResult = Boolean.valueOf((!(((Boolean) value)).booleanValue()));
        }
      }
      if (!_matched) {
        boolean _and_1 = false;
        boolean _equals_1 = Objects.equal(op, "+");
        if (!_equals_1) {
          _and_1 = false;
        } else {
          _and_1 = (_equals_1 && (value instanceof Number));
        }
        if (_and_1) {
          _matched=true;
          _switchResult = value;
        }
      }
      if (!_matched) {
        ConstantExpressionEvaluationException _constantExpressionEvaluationException = new ConstantExpressionEvaluationException(((("Couldn\'t evaluate unary operator \'" + value) + "\' on value ") + value));
        throw _constantExpressionEvaluationException;
      }
      _xblockexpression = (_switchResult);
    }
    return _xblockexpression;
  }
  
  protected JvmTypeReference toTypeReference(final JvmType type, final int arrayDimensions) {
    boolean _equals = Objects.equal(type, null);
    if (_equals) {
      return null;
    }
    JvmParameterizedTypeReference _createJvmParameterizedTypeReference = TypesFactory.eINSTANCE.createJvmParameterizedTypeReference();
    final Procedure1<JvmParameterizedTypeReference> _function = new Procedure1<JvmParameterizedTypeReference>() {
      public void apply(final JvmParameterizedTypeReference it) {
        it.setType(type);
      }
    };
    JvmTypeReference resultTypeRef = ObjectExtensions.<JvmParameterizedTypeReference>operator_doubleArrow(_createJvmParameterizedTypeReference, _function);
    ExclusiveRange _doubleDotLessThan = new ExclusiveRange(0, arrayDimensions, true);
    for (final Integer i : _doubleDotLessThan) {
      {
        final JvmGenericArrayTypeReference arrayRef = TypesFactory.eINSTANCE.createJvmGenericArrayTypeReference();
        arrayRef.setComponentType(resultTypeRef);
        resultTypeRef = arrayRef;
      }
    }
    return resultTypeRef;
  }
  
  protected Class<? extends Object> getJavaType(final JvmType type, final ClassFinder classFinder) throws ClassNotFoundException {
    if ((type instanceof JvmArrayType)) {
      JvmType t = type;
      String dimensions = "";
      boolean _while = (t instanceof JvmArrayType);
      while (_while) {
        {
          dimensions = (dimensions + "[]");
          JvmComponentType _componentType = ((JvmArrayType) t).getComponentType();
          t = _componentType;
        }
        _while = (t instanceof JvmArrayType);
      }
      final Class<? extends Object> componentClass = this.getJavaType(t, classFinder);
      String _name = componentClass.getName();
      String _plus = (_name + dimensions);
      return classFinder.forName(_plus);
    }
    String _identifier = type.getIdentifier();
    boolean _equals = Objects.equal(_identifier, "java.lang.Class");
    if (_equals) {
      return JvmTypeReference.class;
    }
    if ((type instanceof JvmEnumerationType)) {
      return JvmEnumerationLiteral.class;
    }
    if ((type instanceof JvmAnnotationType)) {
      return XAnnotation.class;
    }
    String _identifier_1 = type.getIdentifier();
    return classFinder.forName(_identifier_1);
  }
  
  protected String toText(final XExpression expression) {
    ICompositeNode _node = NodeModelUtils.getNode(expression);
    String _text = _node.getText();
    return _text;
  }
  
  public Object internalEvaluate(final XExpression it, final Context ctx) {
    if (it instanceof XBinaryOperation) {
      return _internalEvaluate((XBinaryOperation)it, ctx);
    } else if (it instanceof XFeatureCall) {
      return _internalEvaluate((XFeatureCall)it, ctx);
    } else if (it instanceof XListLiteral) {
      return _internalEvaluate((XListLiteral)it, ctx);
    } else if (it instanceof XMemberFeatureCall) {
      return _internalEvaluate((XMemberFeatureCall)it, ctx);
    } else if (it instanceof XUnaryOperation) {
      return _internalEvaluate((XUnaryOperation)it, ctx);
    } else if (it instanceof XBooleanLiteral) {
      return _internalEvaluate((XBooleanLiteral)it, ctx);
    } else if (it instanceof XCastedExpression) {
      return _internalEvaluate((XCastedExpression)it, ctx);
    } else if (it instanceof XNumberLiteral) {
      return _internalEvaluate((XNumberLiteral)it, ctx);
    } else if (it instanceof XStringLiteral) {
      return _internalEvaluate((XStringLiteral)it, ctx);
    } else if (it instanceof XTypeLiteral) {
      return _internalEvaluate((XTypeLiteral)it, ctx);
    } else if (it instanceof XAnnotation) {
      return _internalEvaluate((XAnnotation)it, ctx);
    } else if (it != null) {
      return _internalEvaluate(it, ctx);
    } else {
      throw new IllegalArgumentException("Unhandled parameter types: " +
        Arrays.<Object>asList(it, ctx).toString());
    }
  }
  
  public String getFullName(final XExpression call) {
    if (call instanceof XFeatureCall) {
      return _getFullName((XFeatureCall)call);
    } else if (call instanceof XMemberFeatureCall) {
      return _getFullName((XMemberFeatureCall)call);
    } else if (call != null) {
      return _getFullName(call);
    } else {
      throw new IllegalArgumentException("Unhandled parameter types: " +
        Arrays.<Object>asList(call).toString());
    }
  }
}