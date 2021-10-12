grammar Taxi;

// starting point for parsing a taxi file
document
    :   (singleNamespaceDocument | multiNamespaceDocument)
    ;

singleNamespaceDocument
    :  importDeclaration* namespaceDeclaration? toplevelObject* EOF
    ;

multiNamespaceDocument
    : importDeclaration* namespaceBlock* EOF
    ;

importDeclaration
    :   'import' qualifiedName
    ;

namespaceDeclaration
    :   'namespace' qualifiedName
    ;

namespaceBlock
    :   'namespace' qualifiedName namespaceBody
    ;


namespaceBody
    : '{' toplevelObject* '}'
    ;

toplevelObject
    :   typeDeclaration
    |   enumDeclaration
    |   enumExtensionDeclaration
    |   typeExtensionDeclaration
    |   typeAliasDeclaration
    |   typeAliasExtensionDeclaration
    |   serviceDeclaration
    |   policyDeclaration
    |   functionDeclaration
    |   annotationTypeDeclaration
    |   query
    |   viewDeclaration
    ;

typeModifier
// A Parameter type indicates that the object
// is used when constructing requests,
// and that frameworks should freely construct
// these types based on known values.
    : 'parameter'
    | 'closed'
    ;

typeKind : 'type' | 'model';

typeDeclaration
    :  typeDoc? annotation* typeModifier* typeKind Identifier

        ('inherits' listOfInheritedTypes)?
        (typeBody | expressionTypeDeclaration)?
    ;

listOfInheritedTypes
    : typeType (',' typeType)*
    ;
typeBody
    :   '{' (typeMemberDeclaration | conditionalTypeStructureDeclaration )* '}'
    ;

typeMemberDeclaration
     :   typeDoc? annotation* fieldDeclaration
     ;

expressionTypeDeclaration : 'by' expressionGroup*;

// (A,B) -> C
// Used in functions:
// declare function <T,A> sum(T[], (T) -> A):A
// Note - this is used when the lambda is declared, not when
// it's used in a function as an expression.
// eg:
// given the sum example above, it's usage would be:
// model Output {
// total : Int by sum(this.transactions, (Transaction) -> Cost)
//}
// In that example, the sum(this.transactions, (Transaction) -> Cost) is
// an exmpression, not a lambdaSignature
lambdaSignature: expressionInputs typeType;


expressionInputs: '(' expressionInput (',' expressionInput)* ')' '->';
expressionInput: (Identifier ':')? typeType;


// Added for expression types.
// However, I suspect with a few modifications e can simplify
// all expressions to
// this group (fields, queries, etc.).
// This definition is based off of this:
// https://github.com/antlr/grammars-v4/blob/master/arithmetic/arithmetic.g4
// which ensures order-of-precedence and supports grouped / parenthesis
// expressions
expressionGroup:
   expressionGroup POW expressionGroup
   | expressionGroup (MULT | DIV) expressionGroup
   | expressionGroup (PLUS | MINUS) expressionGroup
   | LPAREN expressionGroup RPAREN
   | (PLUS | MINUS)* expressionAtom
   // The below is added for lambdas, but not sure order of precedence
   // is correct. TBD.
   | expressionGroup comp_operator expressionGroup
   | expressionGroup LOGICAL_AND expressionGroup
   | expressionGroup LOGICAL_OR expressionGroup
   // Inputs go last, so that when parsing lambdas, the inputs are the LHS and everything remainin goes RHS.
   // Might not work for nested lambdas, if that's a thing.
   | expressionInputs expressionGroup;

// readFunction before typeType to avoid functons being identified
// as types
// 1-Oct: Tried collapsing scalarAccessorExpression into this, but it caused errors.
// Would like to simplify...
expressionAtom: readFunction | typeType | fieldReferenceSelector | modelAttributeTypeReference | literal | anonymousTypeDefinition;

//scalarAccessorExpression
  //    : xpathAccessorDeclaration
  //    | jsonPathAccessorDeclaration
  //    | columnDefinition
  //    | defaultDefinition
  //    | readFunction
  //    | readExpression
  //    | byFieldSourceExpression
  //    | collectionProjectionExpression
  //   | conditionalTypeConditionDeclaration

annotationTypeDeclaration
   : typeDoc? annotation* 'annotation' Identifier annotationTypeBody?;

annotationTypeBody: '{' typeMemberDeclaration* '}';



// Deprecated - use expressionGroups instead
//fieldExpression
//   : '(' propertyToParameterConstraintLhs arithmaticOperator propertyToParameterConstraintLhs ')'
//   ;

conditionalTypeStructureDeclaration
    :
   '(' typeMemberDeclaration* ')' 'by' conditionalTypeConditionDeclaration
   ;

conditionalTypeConditionDeclaration:
//   (fieldExpression |
   conditionalTypeWhenDeclaration;
//   );

conditionalTypeWhenDeclaration:
   'when' ('(' expressionGroup ')')? '{'
   conditionalTypeWhenCaseDeclaration*
   '}';

// field references must be prefixed by this. -- ie., this.firstName
// this is to disambiguoate lookups by type -- ie., Name
//
// Note: Have had to relax the requirement for propertyFieldNameQualifier
// to be mandatory, as this created bacwards comapatbility issues
// in when() clauses
//
// Update: In the type expressions feature branch
// I've remove the relaxed requirement, re-enforcing that
// field refereces must be prefixed.
// Otherwise, these no lexer difference between
// a fieldReferenceSelector (not permitted in expression types)
// and a typeReferenceSelector (which is permitted)
fieldReferenceSelector: propertyFieldNameQualifier Identifier;
typeReferenceSelector: typeType;

conditionalTypeWhenCaseDeclaration:
   caseDeclarationMatchExpression '->' (  /*caseFieldAssignmentBlock |  */  expressionGroup | scalarAccessorExpression | modelAttributeTypeReference);

caseDeclarationMatchExpression: // when( ... ) {
   expressionGroup |
   caseElseMatchExpression;

caseElseMatchExpression: 'else';

caseFieldAssigningDeclaration :  // dealtAmount ...  (could be either a destructirng block, or an assignment)
   Identifier (
      caseFieldDestructuredAssignment | // dealtAmount ( ...
      ( EQ caseScalarAssigningDeclaration ) | // dealtAmount = ccy1Amount | dealtAmount = 'foo'
      // TODO : How do we model Enum assignments here?
      // .. some enum assignment ..
      accessor
   );

caseScalarAssigningDeclaration:
   expressionGroup | scalarAccessorExpression;

caseFieldDestructuredAssignment :  // dealtAmount ( ... )
     '(' caseFieldAssigningDeclaration* ')';

fieldModifier
   : 'closed'
   ;
fieldDeclaration
  :   fieldModifier? Identifier (':' (simpleFieldDeclaration | anonymousTypeDefinition | modelAttributeTypeReference))?
  ;

// Used in queries to scope projection of collections.
// eg:
//findAll { OrderTransaction[] } as {
//   items: Thing[] by [OrderItem]
// }[]
collectionProjectionExpression: '[' typeType ']' ;


// A type reference that refers to the attribute on a model.
// eg:  firstName : Person::FirstName.
// Only meaningful within views.
modelAttributeTypeReference: typeType '::' typeType;

simpleFieldDeclaration: typeType accessor?;

typeType
    :   classOrInterfaceType typeArguments? listType? optionalType? parameterConstraint? (aliasedType? | inlineInheritedType?)?
    ;

accessor
    : 'by' scalarAccessorExpression
    ;

scalarAccessorExpression
    : xpathAccessorDeclaration
    | jsonPathAccessorDeclaration
    | columnDefinition
    | defaultDefinition
    | readFunction
    | readExpression
    | byFieldSourceExpression
    | collectionProjectionExpression
    | conditionalTypeConditionDeclaration
    ;

// Required for Query based Anonymous type definitions like:
// {
//               traderEmail: UserEmail (by this.traderId)
// }
//
byFieldSourceExpression:  typeType '['  StringLiteral  ']';
xpathAccessorDeclaration : 'xpath' '(' StringLiteral ')';
jsonPathAccessorDeclaration : 'jsonPath' '(' StringLiteral ')';


// Deprecating and removing this.
// It was never used, and is confusing
//objectAccessor
//    : '{' destructuredFieldDeclaration* '}'
//    ;
//
//destructuredFieldDeclaration
//    : Identifier accessor
//    ;

//accessorExpression : StringLiteral;

classOrInterfaceType
    :   Identifier /* typeArguments? */ ('.' Identifier /* typeArguments? */ )*
    ;

typeArguments: '<' typeType (',' typeType)* '>';

// A "lenient" enum will match on case insensitive values
enumDeclaration
    :    typeDoc? annotation* lenientKeyword? 'enum' classOrInterfaceType
         (('inherits' enumInheritedType) | ('{' enumConstants? '}'))
    ;

enumInheritedType
    : typeType
    ;

enumConstants
    :   enumConstant (',' enumConstant)*
    ;

enumConstant
    :   typeDoc? annotation*  defaultKeyword? Identifier enumValue? enumSynonymDeclaration?
    ;

enumValue
   : '(' literal ')'
   ;

enumSynonymDeclaration
   : 'synonym' 'of' ( enumSynonymSingleDeclaration | enumSynonymDeclarationList)
   ;
enumSynonymSingleDeclaration : qualifiedName ;
enumSynonymDeclarationList : '[' qualifiedName (',' qualifiedName)* ']'
   ;
 enumExtensionDeclaration
    : typeDoc? annotation* 'enum extension' Identifier  ('{' enumConstantExtensions? '}')?
    ;

enumConstantExtensions
    :   enumConstantExtension (',' enumConstantExtension)*
    ;

enumConstantExtension
   : typeDoc? annotation* Identifier enumSynonymDeclaration?
   ;

// type aliases
typeAliasDeclaration
    : typeDoc? annotation* 'type alias' Identifier aliasedType
    ;

aliasedType
   : 'as' typeType
   ;

inlineInheritedType
   : 'inherits' typeType
   ;

typeAliasExtensionDeclaration
   : typeDoc? annotation* 'type alias extension' Identifier
   ;
// Annotations
annotation
    :   '@' qualifiedName ( '(' ( elementValuePairs | elementValue )? ')' )?
    ;

elementValuePairs
    :   elementValuePair (',' elementValuePair)*
    ;

elementValuePair
    :   Identifier '=' elementValue
    ;

elementValue
    :   literal
    |    qualifiedName // Support enum references within annotations
    |   annotation
    ;

serviceDeclaration
    : typeDoc? annotation* 'service' Identifier serviceBody
    ;

serviceBody
    :   '{' lineageDeclaration? serviceBodyMember* '}'
    ;
serviceBodyMember : serviceOperationDeclaration | queryOperationDeclaration;
// Querying
queryOperationDeclaration
   :  typeDoc? annotation* queryGrammarName 'query' Identifier '(' operationParameterList ')' ':' typeType
      'with' 'capabilities' '{' queryOperationCapabilities '}';

queryGrammarName : Identifier;
queryOperationCapabilities: (queryOperationCapability (',' queryOperationCapability)*);

queryOperationCapability:
   queryFilterCapability | Identifier;

queryFilterCapability: 'filter'( '(' filterCapability (',' filterCapability)* ')');

filterCapability: EQ | NQ | IN | LIKE | GT | GE | LT | LE;

lineageDeclaration
      : typeDoc? annotation* 'lineage' lineageBody;

lineageBody
      : '{' lineageBodyMember* '}';

lineageBodyMember
      : consumesBody | storesBody;

consumesBody: 'consumes' 'operation' qualifiedName;

storesBody: 'stores' qualifiedName;


serviceOperationDeclaration
     : typeDoc? annotation* operationScope? 'operation'  operationSignature
     ;

operationSignature
     :   annotation* Identifier  '(' operationParameterList? ')' operationReturnType?
     ;

operationScope : Identifier;

operationReturnType
    : ':' typeType
    ;
operationParameterList
    :   operationParameter (',' operationParameter)*
    ;


operationParameter
// Note that only one operationParameterConstraint can exist per parameter, but it can contain
// multiple expressions
     :   annotation* (parameterName)? ((typeType varargMarker?) | lambdaSignature)
     ;

varargMarker: '...';
// Parameter names are optional.
// But, they must be used to be referenced in return contracts
parameterName
    :   Identifier ':'
    ;

parameterConstraint
    :   '(' parameterConstraintExpressionList ')'
    |   '(' temporalFormatList ')'
    ;


parameterConstraintExpressionList
    :  parameterConstraintExpression (',' parameterConstraintExpression)*
    ;

parameterConstraintExpression
    :  propertyToParameterConstraintExpression
    |  operationReturnValueOriginExpression
    |  propertyFormatExpression
    ;

// First impl.  This will get richer (',' StringLiteral)*
propertyFormatExpression :
   '@format' '=' StringLiteral;

temporalFormatList :
   ('@format' '=' '[' StringLiteral (',' StringLiteral)* ']')? ','? (instantOffsetExpression)?
   ;

instantOffsetExpression :
   '@offset' '=' IntegerLiteral;

// The return value will have a relationship to a property
// received in an input (incl. nested properties)
operationReturnValueOriginExpression
    :  'from' qualifiedName
    ;

// A parameter will a value that matches a specified expression
// operation convertCurrency(request : ConversionRequest) : Money( this.currency = request.target )
// Models a constraint against an attribute on the type (generally return type).
// The attribute is identified by EITHER
// - it's name -- using this.fieldName
// - it's type (preferred) using TheTypeName
// The qualifiedName here is used to represent a path to the attribute (this.currency)
// We could've just used Identifier here, but we'd like to support nested paths
propertyToParameterConstraintExpression
   : propertyToParameterConstraintLhs comparisonOperator propertyToParameterConstraintRhs;

propertyToParameterConstraintLhs : (propertyFieldNameQualifier? qualifiedName)? | modelAttributeTypeReference?;
propertyToParameterConstraintRhs : (literal | qualifiedName);

propertyFieldNameQualifier : 'this' '.';

comp_operator : GT
              | GE
              | LT
              | LE
              | EQ
              | NQ
              ;

comparisonOperator
   : '=='
   | '>'
   | '>='
   | '<='
   | '<'
   | '!='
   ;

policyDeclaration
    :  annotation* 'policy' policyIdentifier 'against' typeType '{' policyRuleSet* '}';

policyOperationType
    : Identifier;

policyRuleSet : policyOperationType policyScope? '{' (policyBody | policyInstruction) '}';

policyScope : 'internal' | 'external';


policyBody
    :   policyStatement*
    ;

policyIdentifier : Identifier;

policyStatement
    : policyCase | policyElse;

// TODO: Should consider revisiting this, so that operators are followed by valid tokens.
// eg: 'in' must be followed by an array.  We could enforce this at the language, to simplify in Vyne
policyCase
    : 'case' policyExpression policyOperator policyExpression '->' policyInstruction
    ;

policyElse
    : 'else' '->' policyInstruction
    ;
policyExpression
    : callerIdentifer
    | thisIdentifier
    | literalArray
    | literal;


callerIdentifer : 'caller' '.' typeType;
thisIdentifier : 'this' '.' typeType;

// TODO: Should consider revisiting this, so that operators are followed by valid tokens.
// eg: 'in' must be followed by an array.  We could enforce this at the language, to simplify in Vyne
policyOperator
    : EQ
    | NQ
    | IN
    ;

literalArray
    : '[' literal (',' literal)* ']'
    ;

policyInstruction
    : policyInstructionEnum
    | policyFilterDeclaration
    ;

policyInstructionEnum
    : 'permit';

policyFilterDeclaration
    : 'filter' filterAttributeNameList?
    ;

filterAttributeNameList
    : '(' Identifier (',' Identifier)* ')'
    ;

// processors currently disabled
// https://gitlab.com/vyne/vyne/issues/52
//policyProcessorDeclaration
//    : 'process' 'using' qualifiedName policyProcessorParameterList?
//    ;

//policyProcessorParameterList
//    : '(' policyParameter (',' policyParameter)* ')'
//    ;

//policyParameter
//    : literal | literalArray;
//

columnDefinition : 'column' '(' columnIndex ')' ;

// qualifiedName here is to reference enums
defaultDefinition: 'default' '(' (literal | qualifiedName) ')';

// "declare function" borrowed from typescript.
// Note that taxi supports declaring a function, but won't provide
// an implementation of it.  That'll be down to individual libraries
// Note - intentional decision to enforce these functions to return something,
// rather than permitting void return types.
// This is because in a mapping declaration, functions really only have purpose if
// they return things.
functionDeclaration: typeDoc? 'declare' (functionModifiers)? 'function' typeArguments? functionName '(' operationParameterList? ')' ':' typeType;

functionModifiers: 'query';


readFunction: functionName '(' formalParameterList? ')';
//         'concat' |
//         'leftAndUpperCase' |
//         'midAndUpperCase'
//         ;
readExpression: expressionGroup; //  readFunction arithmaticOperator literal;
functionName: qualifiedName;
formalParameterList
    : parameter  (',' parameter)*
    ;
//    scalarAccessorExpression
      //    : xpathAccessorDeclaration
      //    | jsonPathAccessorDeclaration
      //    | columnDefinition
      //    | conditionalTypeConditionDeclaration
      //    | defaultDefinition
      //    | readFunction
      //    ;
parameter: literal |  scalarAccessorExpression | fieldReferenceSelector | typeReferenceSelector | modelAttributeTypeReference | expressionGroup;

columnIndex : IntegerLiteral | StringLiteral;

expression
    :   primary
    ;

primary
    :   '(' expression ')'
//    |   'this'
//    |   'super'
    |   literal
    |   Identifier
//    |   typeType '.' 'class'
//    |   'void' '.' 'class'
//    |   nonWildcardTypeArguments (explicitGenericInvocationSuffix | 'this' arguments)
    ;

qualifiedName
    :   Identifier ('.' Identifier)*
    ;

listType
   : '[]'
   ;

optionalType
   : '?'
   ;

//primitiveType
//    : primitiveTypeName
//    | 'lang.taxi.' primitiveTypeName
//    ;
//
//primitiveTypeName
//    :   'Boolean'
//    |   'String'
//    |   'Int'
//    |   'Double'
//    |   'Decimal'
////    The "full-date" notation of RFC3339, namely yyyy-mm-dd. Does not support time or time zone-offset notation.
//    |   'Date'
////    The "partial-time" notation of RFC3339, namely hh:mm:ss[.ff...]. Does not support date or time zone-offset notation.
//    |   'Time'
//// Combined date-only and time-only with a separator of "T", namely yyyy-mm-ddThh:mm:ss[.ff...]. Does not support a time zone offset.
//    |   'DateTime'
//// A timestamp, indicating an absolute point in time.  Includes timestamp.  Should be rfc3339 format.  (eg: 2016-02-28T16:41:41.090Z)
//    |   'Instant'
//    |  'Any'
//    ;

// https://github.com/raml-org/raml-spec/blob/master/versions/raml-10/raml-10.md#date
literal
    :   IntegerLiteral
    |   DecimalLiteral
    |   BooleanLiteral
    |   StringLiteral
    |   'null'
    ;

typeExtensionDeclaration
   :  typeDoc? annotation* 'type extension' Identifier typeExtensionBody
   ;

typeExtensionBody
    :   '{' typeExtensionMemberDeclaration* '}'
    ;

typeExtensionMemberDeclaration
    :   annotation* typeExtensionFieldDeclaration
    ;

typeExtensionFieldDeclaration
    :   Identifier typeExtensionFieldTypeRefinement?
    ;

typeExtensionFieldTypeRefinement
    : ':' typeType constantDeclaration?
    ;

constantDeclaration : 'by'  defaultDefinition;
// Typedoc is a special documentation block that wraps types.
// It's treated as plain text, but we'll eventually support doc tools
// that speak markdown.
// Comment markers are [[ .... ]], as this is less likely to generate clashes.
typeDoc : DOCUMENTATION;
// : '[[' ('//' |  ~']]' | '"' | '\'')* ']]';


lenientKeyword: 'lenient';
defaultKeyword: 'default';

/*
 * Taxi QL
 */

queryDocument: importDeclaration* query EOF;

query: namedQuery | anonymousQuery;

namedQuery: queryName '{' queryBody '}';
anonymousQuery: queryBody;

queryName: 'query' Identifier queryParameters?;

queryParameters: '(' queryParamList ')';

queryParamList: queryParam (',' queryParam)*;

queryParam: Identifier ':' typeType;

// findAllDirective: 'findAll';
// findOneDirective: 'findAll';

queryDirective: FindAll | FindOne | Stream | Find;
findDirective: Find;

givenBlock : 'given' '{' factList '}';

factList : fact (',' fact)*;

// TODO :  We could/should make variableName optional
fact : variableName typeType '=' literal;

variableName: Identifier ':';
queryBody:
   givenBlock?
	queryDirective '{' queryTypeList '}' queryProjection?
	;

queryTypeList: typeType (',' typeType)*;

queryProjection: 'as' typeType? anonymousTypeDefinition?;
//as {
//    orderId // if orderId is defined on the Order type, then the type is inferrable
//    productId: ProductId // Discovered, using something in the query context, it's up to Vyne to decide how.
//    traderEmail : EmailAddress(by this.traderUtCode)
//    salesPerson {
//        firstName : FirstName
//        lastName : LastName
//    }(by this.salesUtCode)
//}
anonymousTypeDefinition: typeBody listType? accessor?;

viewDeclaration
    :  typeDoc? annotation* typeModifier* 'view' Identifier
            ('inherits' listOfInheritedTypes)?
            'with' 'query' '{' findBody (',' findBody)* '}'
    ;

findBody: findDirective '{' findBodyQuery '}' ('as' anonymousTypeDefinition)?;
findBodyQuery: joinTo;
filterableTypeType: typeType ('(' filterExpression ')')?;
joinTo: filterableTypeType ('(' 'joinTo'  filterableTypeType ')')?;
filterExpression
    : LPAREN filterExpression RPAREN           # ParenExp
    | filterExpression AND filterExpression    # AndBlock
    | filterExpression OR filterExpression     # OrBlock
    | propertyToParameterConstraintExpression  # AtomExp
    | in_exprs                                 # InExpr
    | like_exprs                               # LikeExpr
    | not_in_exprs                             # NotInExpr
    ;
in_exprs: qualifiedName IN literalArray;
like_exprs: qualifiedName LIKE literal;
not_in_exprs: qualifiedName NOT_IN literalArray;

NOT_IN: 'not in';
IN: 'in';
LIKE: 'like';
AND : 'and' ;
OR  : 'or' ;

FindAll: 'findAll';
FindOne: 'findOne';
Find: 'find';
Stream: 'stream';

// Must come before Identifier, to capture booleans correctly
BooleanLiteral
    :   TRUE | FALSE
    ;

Identifier
    :   Letter LetterOrDigit*
    | '`' ~('`')+ '`'
    ;

StringLiteral
    :   '"' DoubleQuoteStringCharacter* '"'
    |   '\'' SingleQuoteStringCharacter* '\''
    ;




fragment
DoubleQuoteStringCharacter
    :   ~["\\\r\n]
    |   EscapeSequence
    ;

fragment
SingleQuoteStringCharacter
    :   ~['\\\r\n]
    |   EscapeSequence
    ;

// §3.10.6 Escape Sequences for Character and String Literals

fragment
EscapeSequence
    :   '\\' [btnfr"'\\]
//    |   OctalEscape
//    |   UnicodeEscape
    ;


fragment
Letter
    :   [a-zA-Z$_] // these are the "java letters" below 0x7F
    |   // covers all characters above 0x7F which are not a surrogate
        ~[\u0000-\u007F\uD800-\uDBFF]
    |   // covers UTF-16 surrogate pairs encodings for U+10000 to U+10FFFF
        [\uD800-\uDBFF] [\uDC00-\uDFFF]
    ;

fragment
LetterOrDigit
    :   [a-zA-Z0-9$_] // these are the "java letters or digits" below 0x7F
    |   // covers all characters above 0x7F which are not a surrogate
        ~[\u0000-\u007F\uD800-\uDBFF]
    |   // covers UTF-16 surrogate pairs encodings for U+10000 to U+10FFFF
        [\uD800-\uDBFF] [\uDC00-\uDFFF]
    ;

IntegerLiteral
    :   MINUS? DecimalNumeral /* IntegerTypeSuffix? */
    ;

// Note: Make sure this is defined after IntegerLiteral,
// so that numbers without '.' are parsed as Integers, not
// Decimals.
DecimalLiteral : NUMBER;

fragment
DecimalNumeral
    :   '0'
    |   NonZeroDigit (Digits? | Underscores Digits)
    ;

fragment
Digits
    :   Digit (DigitOrUnderscore* Digit)?
    ;

fragment
Digit
    :   '0'
    |   NonZeroDigit
    ;

fragment
NonZeroDigit
    :   [1-9]
    ;

fragment
DigitOrUnderscore
    :   Digit
    |   '_'
    ;

fragment
Underscores
    :   '_'+
    ;




NAME
   : [_A-Za-z] [_0-9A-Za-z]*
   ;


STRING
   : '"' ( ESC | ~ ["\\] )* '"'
   ;


fragment ESC
   : '\\' ( ["\\/bfnrt] | UNICODE )
   ;


fragment UNICODE
   : 'u' HEX HEX HEX HEX
   ;


fragment HEX
   : [0-9a-fA-F]
   ;


NUMBER
   : '-'? INT '.' [0-9]+ EXP? | '-'? INT EXP | '-'? INT
   ;


fragment INT
   : '0' | [1-9] [0-9]*
   ;

fragment EXP
   : [Ee] [+\-]? INT
   ;

//
// Whitespace and comments
//

WS  :  [ \t\r\n\u000C]+ -> skip
    ;

DOCUMENTATION
   : '[[' .*? ']]';

COMMENT
    :   '/*' .*? '*/' -> channel(HIDDEN)
    ;

LINE_COMMENT
    :   '//' ~[\r\n]* -> channel(HIDDEN)
    ;

GT : '>' ;
GE : '>=' ;
LT : '<' ;
LE : '<=' ;
EQ : '==' ;
NQ : '!=';

LOGICAL_OR : '||';
LOGICAL_AND : '&&';

TRUE  : 'true' ;
FALSE : 'false' ;

MULT  : '*' ;
DIV   : '/' ;
PLUS  : '+' ;
MINUS : '-' ;
POW: '^';

LPAREN : '(' ;
RPAREN : ')' ;
