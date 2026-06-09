package crml.compiler.translation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import crml.language.grammar.crmlBaseVisitor;
import crml.language.grammar.crmlParser;
import crml.language.util.Parser;
import crml.util.SafeResource;
import crml.language.grammar.crmlParser.Category_pairContext;
import crml.language.grammar.crmlParser.Class_var_defContext;
import crml.language.grammar.crmlParser.DependencyContext;
import crml.language.grammar.crmlParser.Element_defContext;
import crml.language.grammar.crmlParser.ExpContext;
import crml.language.grammar.crmlParser.IdContext;
import crml.language.grammar.crmlParser.User_keywordContext;

public class crmlVisitorImpl extends crmlBaseVisitor<Value> {

	private Integer counter;

	// type mapping from CRML built-in to Modelica
	private HashMap<String, String> types_mapping;

	// operator mapping CRML built-in to Modelica
	private HashMap<String, List<Signature>> operators_map;

	private VariableData variableTable;

	private HashMap<String, Signature> user_operators;

	private StringBuffer localFunctionCalls;

	private static final Logger logger = LogManager.getLogger();

	private CategoryMapping category_map;

	private String current_category = null;
	crmlParser parser;

	private final Set<String> loadedLibraries = new HashSet<>();

	private String prefix = ""; // to keep track of variable prefix

	private String input_prefix;
	private String output_prefix;

	Boolean saveExtrnal = false;
	List<String> external_variables;

	public crmlVisitorImpl(crmlParser parser, List<String> external_variables, Boolean causal) {
		this(parser, causal);
		saveExtrnal = true;
		this.external_variables = external_variables;
	}

	/**
	 * 
	 * @param parser
	 * @param causal whether to genrerate input/output prefixes for every variable
	 *               in Modelica
	 */
	public crmlVisitorImpl(crmlParser parser, Boolean causal) {

		// FIXME check that class name and class file match

		this.parser = parser;

		if (causal) {
			input_prefix = "input";
			output_prefix = "output";
		} else {
			input_prefix = "";
			output_prefix = "";
		}
		;

		types_mapping = new HashMap<String, String>();

		// table for mapping CRML built in types to Modelica types
		types_mapping.put("Boolean", "CRMLtoModelica.Types.Boolean4");
		types_mapping.put("Period", "CRMLtoModelica.Types.CRMLPeriod");
		types_mapping.put("Periods", "CRMLtoModelica.Types.CRMLPeriods");
		types_mapping.put("Event", "CRMLtoModelica.Types.Event");
		types_mapping.put("Requirement", "CRMLtoModelica.Types.Boolean4");
		types_mapping.put("Clock", "CRMLtoModelica.Types.CRMLClock");
		types_mapping.put("Real", "Real");
		types_mapping.put("Integer", "Integer");
		types_mapping.put("String", "String");

		operators_map = OperatorMapping.get_operator_map();

		category_map = new CategoryMapping();

		localFunctionCalls = new StringBuffer();

		user_operators = new HashMap<String, Signature>();

		counter = 0; // used to create unique names for automatically generated blocks

		variableTable = new VariableData();
	}

	@Override
	public Value visitDefinition(crmlParser.DefinitionContext ctx) {
		StringBuffer buffer = new StringBuffer();

		// TODO support for library and package
		if (!ctx.definition_type().getText().equals("model"))
			throw new ParseCancellationException("library and package not implemented yet");

		buffer.append("model " + ctx.id().getText() + " \n");

		// Load libraries first so operators are registered before elements are visited
		if (!ctx.dependency().isEmpty()) {
			for (DependencyContext d : ctx.dependency()) {
				for (IdContext libId : d.id()) {
					buffer.append(visitLibrary(libId.getText()));
				}
			}
		}

		List<Element_defContext> cL = ctx.element_def();
		for (Element_defContext e : cL)
			buffer.append(visit(e).toModelica());

		buffer.append(localFunctionCalls);

		buffer.append("end " + ctx.id().getText() + ";\n");

		return new Value(buffer.toString(), "Program");// ??? return visit(ctx.var_def());
	}

	@Override
	public Value visitElement_def(crmlParser.Element_defContext ctx) {
		// the element is a definition
		if (ctx.var_def() != null)
			return visit(ctx.var_def());

		// the element is a template
		if (ctx.template() != null)
			return visit(ctx.template());

		// the element is an operator
		if (ctx.operator() != null)
			return visit(ctx.operator());

		// the element is a class definition
		if (ctx.class_def() != null)
			return visit(ctx.class_def());

		// the element is a category
		if (ctx.category() != null)
			return visit(ctx.category());

		// the element is a parameter or an external variable
		if (ctx.uninstantiated_def() != null)
			return visit(ctx.uninstantiated_def());

		// TODO sets, comments, type

		throw new ParseCancellationException("Unable to translate in element_def : " + ctx.getText() + '\n');
	}

	@Override
	public Value visitUninstantiated_def(crmlParser.Uninstantiated_defContext ctx) {

		String var_type = "", var_modelica_type = "";
		String var_name;
		StringBuffer var_names = new StringBuffer();
		String var_prefix;

		Boolean isSet = null;

		if (ctx.static_qualifier().getText().contentEquals("parameter"))
			var_prefix = "parameter ";
		else {
			var_prefix = "";
			return new Value("", "Category"); /// ??? what happenned with this
		}
		if (ctx.type() != null) {
			// convert the type if it is a built in
			if (ctx.type().builtin_type() != null) {
				var_type = ctx.type().builtin_type().getText();
				var_modelica_type = types_mapping.get(var_type);
			} else {
				var_type = ctx.type().id().getText();
				var_modelica_type = var_type;
			}
			isSet = (ctx.type().empty_set() != null);
			if (isSet)
				var_modelica_type += " [:] ";
		}
		// TODO translate structure types
		else if (ctx.structure_type() != null)
			throw new ParseCancellationException(
					"need to implement structure type translation : " + ctx.getText() + '\n');

		int i = 0;
		for (IdContext e : ctx.id()) {
			var_name = e.getText();
			var_names.append(var_name);

			variableTable.putVariable(var_name, var_type, isSet, prefix);
			variableTable.putlocalVariable(var_name, var_type, isSet);

			i++;
			if (i < ctx.id().size())
				var_names.append(" ,");
		}
		return new Value(var_prefix + var_modelica_type + " " + var_names + ";\n", var_type);
	}

	@Override
	public Value visitCategory(crmlParser.CategoryContext ctx) {
		HashMap<String, String> ctg_pairs = new HashMap<>();
		// TOFIX check that operators being mapped actually exist
		for (Category_pairContext i : ctx.category_pair()) {
			ctg_pairs.put(i.op(0).getText(), i.op(1).getText());
			System.out.println(i.op(0).getText() + " : " + i.op(1).getText() + "\n");
		}
		category_map.addCategory(ctx.id().getText(), ctg_pairs);
		System.err.println("Added category " + ctx.id().getText() + "\n");

		return new Value("", "Category");
	}

	@Override
	public Value visitClass_def(crmlParser.Class_defContext ctx) {
		StringBuffer buffer = new StringBuffer();
		Value val;

		String prefixTemp = prefix;

		if (prefix != "")
			prefix += ".";

		prefix += ctx.id(0).getText();

		StringBuffer store_localFunctionCalls = new StringBuffer(localFunctionCalls);
		localFunctionCalls = new StringBuffer();

		buffer.append("model " + ctx.id(0).getText());

		if (ctx.class_var_def() != null)// parse class variables
			buffer.append("\n");
		for (Class_var_defContext e : ctx.class_var_def()) {
			val = visitClass_var_def(e);

			buffer.append(val.toModelica());
		}

		if (ctx.type() != null) {
			buffer.append(" extends " + ctx.type().getText());

			// TODO translate class parameters
			if (ctx.class_params() != null)
				throw new ParseCancellationException("class parameters not implemented yet");

			buffer.append(";\n");
		}
		buffer.append(localFunctionCalls);
		localFunctionCalls = store_localFunctionCalls;
		buffer.append("end " + ctx.id(0).getText() + "; \n");

		prefix = prefixTemp;
		variableTable.cleanLocalVariables();

		return new Value(buffer.toString(), "Class_Definition");
	}

	@Override
	public Value visitClass_var_def(Class_var_defContext ctx) {

		Value value = null;

		if (ctx.var_def() != null)
			value = visit(ctx.var_def());
		else if (ctx.uninstantiated_def() != null)
			value = visit(ctx.uninstantiated_def());

		else
			// TODO translate class qualifier
			// TODO translate alias, comment, forbid
			throw new ParseCancellationException("unable to translate class: " + ctx.var_def().getText() + '\n');

		return value;
	}

	@Override
	public Value visitOperator(crmlParser.OperatorContext ctx) {
		StringBuffer definition = new StringBuffer("model ");
		StringBuffer modelName = new StringBuffer("'");

		StringBuffer store_localFunctionCalls = new StringBuffer(localFunctionCalls);

		localFunctionCalls = new StringBuffer();

		// generate function name
		for (User_keywordContext k : ctx.operator_def().user_keyword()) {

			String s = k.getText().replace("'", "");
			modelName.append(s);
		}
		modelName.append("'");
		definition.append(modelName);
		definition.append("\n");

		String bType = types_mapping.get(ctx.type().getText());
		if (bType == null)
			bType = ctx.type().getText();

		// keep a list of operator signatures for typing calls
		Signature sig = new Signature();
		sig.return_type = ctx.type().getText();
		sig.return_name = "out";
		sig.function_name = modelName.toString();
		String mtype = bType;

		definition.append(output_prefix + bType + " out; \n");

		// generate variables
		int i = 0;
		for (IdContext v : ctx.operator_def().id()) {
			String type = ctx.operator_def().type().get(i).getText();
			mtype = types_mapping.get(type);
			if (mtype == null)
				mtype = type;
			definition.append(input_prefix + mtype + " " + v.getText() + ";\n");

			// TODO fix set support
			variableTable.putlocalVariable(v.getText(), type, false);

			sig.variable_names.add(v.getText());
			sig.variable_types.add(mtype);
			i++;
		}

		// check for Category
		if (ctx.operator_def().apply_category() != null)
			if (category_map.getCategory(ctx.operator_def().apply_category().id().getText()) != null)
				sig.setCategory(ctx.operator_def().apply_category().id().getText());
			else
				throw new ParseCancellationException(
						"Undefined Category " + ctx.operator_def().apply_category().id().getText());

		user_operators.put(modelName.toString(), sig);

		// append body
		Value exp = visit(ctx.operator_def().exp());
		definition.append(localFunctionCalls + "\n");
		definition.append("equation \n out =" + exp.toModelica() + ";\n");
		definition.append("end ");
		definition.append(modelName);
		definition.append(";\n");

		// get rid of all local variables
		variableTable.cleanLocalVariables();

		// restore local function calls
		localFunctionCalls = store_localFunctionCalls;

		// restore category
		current_category = null;

		return new Value(definition.toString(), "Operator");
	}

	@Override
	public Value visitTemplate(crmlParser.TemplateContext ctx) {

		StringBuffer definition = new StringBuffer("model ");
		StringBuffer modelName = new StringBuffer("'");

		StringBuffer store_localFunctionCalls = new StringBuffer(localFunctionCalls);

		localFunctionCalls = new StringBuffer();

		// generate function name
		for (User_keywordContext k : ctx.user_keyword()) {
			String s = k.getText().replace("'", "");
			modelName.append(s);

		}
		modelName.append("'");
		definition.append(modelName);
		definition.append("\n");

		String bType = types_mapping.get("Boolean");

		// keep a list of operator signatures for typing calls
		Signature sig = new Signature();
		sig.return_type = "Boolean";
		sig.return_name = "out";
		sig.function_name = modelName.toString();

		// generate variables
		for (IdContext v : ctx.id()) {
			definition.append(input_prefix + bType + " " + v.getText() + ";\n");

			// TODO fix sets
			variableTable.putlocalVariable(v.getText(), "Boolean", false);

			sig.variable_names.add(v.getText());
			sig.variable_types.add(bType);
		}

		user_operators.put(modelName.toString(), sig);

		definition.append(output_prefix + bType + " out; \n");

		// append body
		Value exp = visit(ctx.exp());
		definition.append(localFunctionCalls + "\n");
		definition.append("equation \n out =" + exp.toModelica() + ";\n");
		definition.append("end ");
		definition.append(modelName);
		definition.append(";\n");

		// get rid of all local variables
		variableTable.cleanLocalVariables();

		// restore local function calls
		localFunctionCalls = store_localFunctionCalls;

		return new Value(definition.toString(), "Template");
	}

	@Override
	public Value visitVar_def(crmlParser.Var_defContext ctx) {
		String var_t, mapped_t;
		StringBuffer buffer = new StringBuffer();
		Value v;
		Boolean isSet = false;
		var_t = ctx.type().builtin_type() != null
				? ctx.type().builtin_type().getText()
				: ctx.type().id().getText();

		if (types_mapping.containsKey(var_t))
			mapped_t = types_mapping.get(var_t);
		else
			mapped_t = var_t;

		isSet = (ctx.type().empty_set() != null);
		String varName = ctx.id().getText();

		if (isSet) {
			buffer.append("parameter Integer " + varName + "_n = 0;\n  ");
			buffer.append(mapped_t + " " + varName + "[" + varName + "_n]");
		} else {
			buffer.append(mapped_t + " " + varName);
		}

		if (saveExtrnal && ctx.is_external != null)
			external_variables.add(mapped_t + " " + varName + "\n");

		variableTable.putVariable(varName, var_t, isSet, prefix);
		variableTable.putlocalVariable(varName, var_t, isSet);

		if (ctx.arg_list() != null) {
			crmlParser.Arg_listContext args = ctx.arg_list();
			List<crmlParser.Named_argContext> namedArgs = args.named_arg();
			if (!namedArgs.isEmpty()) {
				buffer.append("(\n  ");
				for (int i = 0; i < namedArgs.size(); i++) {
					if (i > 0)
						buffer.append(",\n  ");
					crmlParser.Named_argContext namedArg = namedArgs.get(i);
					String argName = namedArg.id().getText();

					// Set of constructors: transpose into size param + field arrays
					if (namedArg.exp() != null
							&& namedArg.exp().set_def() != null
							&& namedArg.exp().set_def().empty_set() == null) {
						List<crmlParser.ExpContext> elems = namedArg.exp().set_def().exp();
						boolean allCtors = elems.stream()
								.allMatch(e -> e.constructor() != null
										&& e.constructor().arg_list() != null);
						if (allCtors) {
							buffer.append(argName + "_n = " + elems.size());
							List<crmlParser.Named_argContext> fields = elems.get(0).constructor().arg_list()
									.named_arg();
							buffer.append(",\n  " + argName + "(\n");
							for (int j = 0; j < fields.size(); j++) {
								if (j > 0)
									buffer.append(",\n");
								String fieldName = fields.get(j).id().getText();
								buffer.append("    " + fieldName + " = {");
								for (int k = 0; k < elems.size(); k++) {
									if (k > 0)
										buffer.append(", ");
									v = visit(elems.get(k).constructor().arg_list()
											.named_arg().get(j).exp());
									buffer.append(v.toModelica());
								}
								buffer.append("}");
							}
							buffer.append("\n  )");
							continue;
						}
					}

					if (namedArg.arg_list() != null)
						throw new ParseCancellationException("nested arg_list in constructor not implemented yet");
					if (namedArg.exp() == null)
						throw new ParseCancellationException(
								"named argument '" + argName + "' has no value expression (parse error in model?)");
					v = visit(namedArg.exp());
					buffer.append(argName + " = " + v.toModelica());
				}
				buffer.append("\n)");
			}
		} else if (ctx.exp() != null) {
			v = visit(ctx.exp());
			if (!v.type.equals("new")) // check that it is not a constructor
				buffer.append(" = " + v.toModelica());
		}
		buffer.append(";\n");

		return new Value(buffer.toString(), "Definition");

	}

	@Override
	public Value visitExp(crmlParser.ExpContext ctx) {
		Value right, left;

		// if the expression is a constructor
		if (ctx.constructor() != null)
			return visit(ctx.constructor());

		// if the expression is a constant
		if (ctx.constant() != null)
			return visit(ctx.constant());

		// if the expression is a variable
		if (ctx.id() != null)
			return visit(ctx.id());

		// if the expression is a componenent reference
		if (ctx.crml_component_reference() != null) {
			VariableData.VariableType v_type = variableTable.getVariableInfo(ctx.crml_component_reference().getText());
			if (v_type != null)
				return new Value(ctx.getText(), v_type.type, v_type.isSet, v_type.setPath);
			else
				throw new ParseCancellationException(
						"unable to get variable type : " + ctx.crml_component_reference().getText() + '\n');
		}

		// if expression is an if-then-else
		if (ctx.if_exp() != null)
			return visit(ctx.if_exp());

		// if the expression is a built in operator
		if (ctx.builtin_op() != null) {
			String op = null;

			if (current_category != null) // we check if we should apply the category
				op = category_map.getCategory(current_category).get(ctx.builtin_op().getText());
			if (op == null)
				op = ctx.builtin_op().getText();

			System.out.println(
					"Category: " + current_category + " og_op : " + ctx.builtin_op().getText() + " og_op : " + op);

			if (ctx.binary != null) {
				List<crmlParser.ExpContext> vals = new ArrayList<>();
				List<String> ops = new ArrayList<>();
				flattenBinaryChain(ctx, vals, ops);
				return evaluateWithPrecedence(ops, vals);
			} else if (ctx.lunary != null) {
				left = visit(ctx.left);
				Value result = apply_lunary_op(op, left);
				return result;
			}
		}
		if (ctx.runary != null) {
			String op = null;
			if (current_category != null) // we check if we should apply the category
				op = category_map.getCategory(current_category).get(ctx.runary.getText());
			if (op == null)
				op = ctx.runary.getText();

			System.out.println("Category: " + current_category + " og_op : " + ctx.runary.getText() + " og_op : " + op);
			right = visit(ctx.right);
			Value result = apply_runary_op(op, right);
			return result;
		}

		// if the expression is in parenthesis
		if (ctx.sub_exp() != null) {
			crmlParser.Sub_expContext sub = ctx.sub_exp();
			if (sub.user_keyword() != null)
				return apply_user_operator(sub.user_keyword().getText(), new ArrayList<>());
			return visit(sub.exp());
		}

		if (ctx.period_op() != null)
			return visit(ctx.period_op());

		// if the expression is a user defined call
		if (ctx.user_keyword() != null) {
			List<crmlParser.ExpContext> args = new ArrayList<>();

			// put together user operator name
			UserOperatorCall uc = reconstructUserOperator(ctx, "", args);

			String op = null;
			if (current_category != null) // we check if we should apply the category
				op = category_map.getCategory(current_category).get("'" + uc.name + "'");
			if (op == null)
				op = "'" + uc.name + "'";

			String s = "";

			Signature sig = user_operators.get(op);

			if (sig == null) {
				logger.error("LOOKUP FAIL: uc.name=[" + uc.name + "] op=[" + op + "] keys=" + user_operators.keySet());
				throw new ParseCancellationException("no definition found : " + ctx.getText() + '\n');
			}

			int i = 0;

			// check for array iterators
			Value v = null;

			Boolean foundIterator = false;
			for (ExpContext e : uc.args) {
				if (e.iterator() != null && ((sig.variable_is_set == null) || !sig.variable_is_set.get(i))) {
					// flatten iterator
					foundIterator = true;
					// v = new Value (e.id().getText(), sig.return_type, true);

					System.out.println("FOUND iterator " + e.getText().replace(".element", ""));

					v = apply_period_iterator_op(sig, e.getText().replace(".element", ""), i, uc.args);

				}
				i++;
			}
			if (foundIterator)
				return v; // FIXME type checking

			for (ExpContext e : uc.args) {
				s += e.getText().toString() + " ";
			}

			System.out.println("Applying operator: " + uc.name + " " + s + "\n");

			return apply_user_operator(op, uc.args);
		}

		// expression is a tick
		if (ctx.tick() != null) {
			Value cl = visit(ctx.tick().id());
			return apply_lunary_op("tick", cl);
		}

		// expression is an object set

		if (ctx.set_def() != null)
			return visit(ctx.set_def());

		// if expression is integrate
		if (ctx.integrate() != null) {
			Value val = visit(ctx.integrate().exp(0));
			Value on = visit(ctx.integrate().exp(1));

			return apply_binary_op("integrate", val, on);
		}

		throw new ParseCancellationException("unable to parse expression : " + ctx.getText() + '\n');
	}

	private String getOperatorName(ExpContext ctx) {
		if (ctx.user_keyword() == null)
			return "";
		String keyword = ctx.user_keyword().getText().replace("'", "");
		if (ctx.ubinary != null)
			return getOperatorName(ctx.left) + keyword + getOperatorName(ctx.right);
		return keyword + getOperatorName(ctx.exp(0));
	}

	private UserOperatorCall reconstructUserOperator(ExpContext ctx, String string, List<ExpContext> args) {
		if (ctx.user_keyword() != null)// is part of the user operator
			if (ctx.ubinary != null) {// binary operator
				String keyword = ctx.user_keyword().getText().replace("'", "");
				String leftName = getOperatorName(ctx.left);
				String rightName = getOperatorName(ctx.right);
				String compoundName = string + leftName + keyword + rightName;
				// If the compound doesn't exist but the left is already a complete standalone
				// operator, treat it as an opaque argument to the outer binary keyword.
				if (!user_operators.containsKey("'" + compoundName + "'")
						&& !leftName.isEmpty()
						&& user_operators.containsKey("'" + leftName + "'")) {
					UserOperatorCall right = reconstructUserOperator(ctx.right, "", args);
					args.add(ctx.left); // append: left maps to the first (outermost) parameter
					return new UserOperatorCall(string + keyword + right.name, args);
				}
				UserOperatorCall left, right;
				left = reconstructUserOperator(ctx.left, "", args);
				right = reconstructUserOperator(ctx.right, "", args);
				return new UserOperatorCall(compoundName, args);
			} else {
				return reconstructUserOperator(ctx.exp(0), string + ctx.user_keyword().getText().replace("'", ""),
						args);
			}

		args.add(0, ctx);
		return new UserOperatorCall(string, args);
	}

	@Override
	// FIXME proper sets typing
	public Value visitSet_def(crmlParser.Set_defContext ctx) {
		if (ctx.empty_set() != null)
			return new Value(new ArrayList<Value>(), "{}", true);
		List<Value> values = new ArrayList<Value>();
		String type = "{}";
		for (int i = 0; i < ctx.exp().size(); i++) {
			Value v = visit(ctx.exp(i));
			type = v.type;
			values.add(v);
		}
		return new Value(values, type, true);
	}

	@Override
	public Value visitPeriod_op(crmlParser.Period_opContext ctx) {

		Value left = visit(ctx.exp(0));
		Value right = visit(ctx.exp(1));

		Boolean lborder = (ctx.lb.getText().equals("["));
		Boolean rborder = (ctx.rb.getText().equals("]"));

		String varName = "p" + counter++;

		String periodType = types_mapping.get("Period");

		String code = periodType + " " + varName +
				"(isLeftBoundaryIncluded=" + lborder.toString() +
				", isRightBoundaryIncluded=" + rborder.toString() +
				", start_event=" + left.toModelica() +
				", close_event=" + right.toModelica() + ");\n";

		localFunctionCalls.append(code);

		localFunctionCalls.append("CRMLtoModelica.Types.CRMLPeriod_build " + varName + "_init(ps =" + varName + ");\n");

		return new Value(varName, "Period", false);
	}

	@Override
	public Value visitId(crmlParser.IdContext ctx) {
		VariableData.VariableType v_type = variableTable.getVariableInfo(ctx.getText());
		if (v_type != null)
			return new Value(ctx.getText(), v_type.type, v_type.isSet, v_type.setPath);

		else
			throw new ParseCancellationException(
					"unable to get variable type : " + ctx.getText() + '\n');
	}

	@Override
	public Value visitConstructor(crmlParser.ConstructorContext ctx) {

		if (ctx.type().getText().equals("Clock")) { // Clock constructor
			String varName = "c" + counter++;
			String clockType = types_mapping.get("Clock");

			// TODO add return type checking
			Value v = visit(ctx.exp());

			localFunctionCalls.append(clockType + " " + varName + "(b=" + v.toModelica() + ");\n");
			localFunctionCalls
					.append("CRMLtoModelica.Types.CRMLClock_build " + varName + "_init(clock =" + varName + ");\n");
			return new Value(varName, "Clock");
		}

		// if the constructor is for Periods
		if (ctx.type().getText().equals("Periods")) {
			String periodsType = types_mapping.get("Periods");
			String varName = "P" + counter++;
			crmlParser.Period_opContext period = ctx.exp().period_op();
			Value left = visit(period.exp(0));
			Value right = visit(period.exp(1));

			Boolean lborder = (period.lb.getText().equals("["));
			Boolean rborder = (period.rb.getText().equals("]"));

			String code = periodsType + " " + varName +
					"(isLeftBoundaryIncluded=" + lborder.toString() +
					", isRightBoundaryIncluded=" + rborder.toString() +
					", start_event=" + left.toModelica() +
					", close_event=" + right.toModelica() + ");\n";

			localFunctionCalls.append(code);
			localFunctionCalls
					.append("CRMLtoModelica.Types.CRMLPeriods_build " + varName + "_init(P =" + varName + ");\n");

			return new Value(varName, "Periods");
		}

		// Constructor for events
		if (ctx.type().getText().equals("Event")) {

			String eventType = types_mapping.get("Event");
			String e = "e" + counter++;
			Value v = visit(ctx.exp());

			localFunctionCalls.append(eventType + " " + e + "(b=" + v.toModelica() + ");\n");

			localFunctionCalls.append("CRMLtoModelica.Types.CRMLEvent_build " + e + "_init(E =" + e + ");\n");

			return new Value(e, eventType);
		}
		// Constructor with no args - translates to nothing in Modelica
		if (ctx.arg_list() == null && ctx.exp() == null)
			return new Value("", "new");

		// Constructor with named args (e.g., new Pump (ident = "PO1"))
		if (ctx.arg_list() != null) {
			crmlParser.Arg_listContext args = ctx.arg_list();
			List<crmlParser.Named_argContext> namedArgs = args.named_arg();
			StringBuilder argStr = new StringBuilder("(");
			for (int i = 0; i < namedArgs.size(); i++) {
				if (i > 0)
					argStr.append(", ");
				crmlParser.Named_argContext namedArg = namedArgs.get(i);
				if (namedArg.arg_list() != null)
					throw new ParseCancellationException("nested arg_list in constructor not implemented yet");
				if (namedArg.exp() == null)
					throw new ParseCancellationException("named argument '" + namedArg.id().getText()
							+ "' has no value expression (parse error in model?)");
				Value v = visit(namedArg.exp());
				argStr.append(namedArg.id().getText() + " = " + v.toModelica());
			}
			argStr.append(")");
			return new Value(argStr.toString(), "new");
		}
		if (ctx.exp() != null) {
			// Constructor with expression - call corresponding function
			Value exp_val = visit(ctx.exp());
			Value result = apply_lunary_op(ctx.type().getText(), exp_val);
			return result;
		}
		throw new IllegalStateException("Unreachable state. Function must return with with empty, exp, or arg_list.");
	}

	/**
	 * @Override
	 *           public Value
	 *           visitComponent_reference(crmlParser.Component_referenceContext ctx)
	 *           {
	 *           // TODO fix component references
	 * 
	 *           throw new ParseCancellationException("component references not
	 *           implemented yet : " + ctx.toStringTree() + '\n');
	 *           }
	 */

	@Override
	public Value visitIf_exp(crmlParser.If_expContext ctx) {

		Value value_if, value_then, value_else;

		value_if = visit(ctx.if_e);

		value_then = visit(ctx.then_e);

		if (ctx.else_e != null) {
			value_else = visit(ctx.else_e);

			return new Value(" if (" + value_if.toModelica() + "== CRMLtoModelica.Types.Boolean4.true4) then "
					+ value_then.toModelica() + " else " + value_else.toModelica(),
					value_then.type);

		}
		return new Value(" if " + value_if.toModelica() + " then "
				+ value_then.toModelica(),
				value_then.type);
	}

	private Value apply_user_operator(String op, List<ExpContext> exp) {
		String previous_category = null;
		// check if the operator is defined
		// System.out.println("APPLYING OPERATOR " + op + "\n");
		Signature sign = user_operators.get(op);
		if (sign == null)
			throw new ParseCancellationException("User operator undefined : " + op + "\n");

		if (sign.getCategory() != null) {
			previous_category = current_category;
			current_category = sign.getCategory();
		}
		String name = op.substring(0, op.length() - 1).replace(".", "_") + counter + '\'';

		String res = "";

		for (int i = 0; i < exp.size(); i++) {
			ExpContext e = exp.get(i);
			Value operand = visit(e);
			res += sign.variable_names.get(exp.size() - i - 1) + "=" + operand.toModelica();
			if (i < exp.size() - 1)
				res += ", ";
		}
		res = sign.function_name + " " + name + "(" + res + ");\n";

		localFunctionCalls.append(res);
		counter++;

		current_category = previous_category; // restore
		return new Value(name + ".out", sign.return_type);

	}

	private static String normalizeType(String type) {
		return "Requirement".equals(type) ? "Boolean" : type;
	}

	private Value apply_runary_op(String op, Value right) {

		Signature op_t = OperatorMapping.is_defined(operators_map, op, normalizeType(right.type), right.isSet);

		if (op_t == null)
			throw new ParseCancellationException("Built in operator undefined : " + op + " on " + right.type + '\n');

		if (op_t.mtype == Signature.Type.OPERATOR) { // check if predefined operator maps to Modelica operator

			// special case if the return is boolean and needs to be wrapped in a CRML
			// boolean
			if (op_t.return_type.equals("Boolean"))
				return new Value("CRMLtoModelica.Functions.cvBooleanToBoolean4(" + op_t.function_name + " "
						+ right.toModelica() + ")", "Boolean");

			return new Value(op_t.function_name + " " + right.toModelica(), op_t.return_type);
		} else if (op_t.mtype == Signature.Type.FUNCTION) {
			return new Value(op_t.function_name + "(" + right.toModelica() + ")", op_t.return_type);

		}

		// operator translates to block instantiation
		String name = op_t.function_name.replace(".", "_") + counter;

		String res = op_t.function_name + " " + name + "(" + op_t.variable_names.get(0) + " = " + right.toModelica()
				+ ");\n";
		localFunctionCalls.append(res);
		counter++;

		return new Value(name + ".out", op_t.return_type, op_t.is_return_set);

	}

	private Value apply_lunary_op(String op, Value left) {
		System.out.println("OP DEBUG: " + op + "  " + left.type + " " + left.toModelica() + left.isSet);
		Signature op_t = OperatorMapping.is_defined(operators_map, op, normalizeType(left.type), left.isSet);

		if (op_t == null)
			throw new ParseCancellationException(
					"Built in operator undefined : " + op + " on " + left.type + " isSet " + left.isSet + '\n');

		if (op_t.mtype == Signature.Type.OPERATOR) { // check if predefined operator maps to Modelica operator

			// special case if the return is boolean and needs to be wrapped in a CRML
			// boolean
			if (op_t.return_type.equals("Boolean"))
				return new Value("CRMLtoModelica.Functions.cvBooleanToBoolean4(" + op_t.function_name + " "
						+ left.toModelica() + ")", "Boolean");

			return new Value(op_t.function_name + " " + left.toModelica(), op_t.return_type, op_t.is_return_set);
		} else if (op_t.mtype == Signature.Type.FUNCTION) {
			return new Value(op_t.function_name + "(" + " " + left.toModelica() + ")", op_t.return_type,
					op_t.is_return_set);

		}

		// operator translates to block instantiation
		String name = op_t.function_name.replace(".", "_") + counter;

		String res = op_t.function_name + " " + name + "(" + op_t.variable_names.get(0) + " = " + left.toModelica()
				+ ");\n";
		localFunctionCalls.append(res);
		counter++;

		return new Value(name + ".out", op_t.return_type, op_t.is_return_set);
	}

	private Value apply_period_iterator_op(Signature sig, String v_name, int arg_index, List<ExpContext> args) {

		// translates to block instantiation

		String name = sig.function_name.replace("\'", "") + "_iteraor" + counter++;
		String res = "model " + name + "\n";

		String f_name = sig.function_name.replace("'", "") + counter++;
		res += "CRMLtoModelica.Types.CRMLPeriods ps;\n";
		res += types_mapping.get(sig.return_type) + "[50] out;\n  ";
		res += sig.function_name + "[50]" + f_name + ";\n";

		String val = " equation\n";
		val += "for i in 1:" + "size(ps.period, 1)" + " loop\n";

		// APPLY operator
		String previous_category = null;

		// check if the operator is defined
		// System.out.println("APPLYING OPERATOR " + op + "\n");
		Signature sign = user_operators.get(sig.function_name);

		if (sign == null)
			throw new ParseCancellationException("User operator undefined : " + sig.function_name + "\n");

		if (sign.getCategory() != null) {
			previous_category = current_category;
			current_category = sign.getCategory();
		}

		String pass_args = "";

		for (int i = 0; i < args.size(); i++) {
			if (i == arg_index)
				val += f_name + "[i]." + sign.variable_names.get(args.size() - i - 1) + " = ps.period[i];\n";
			else {
				ExpContext e = args.get(i);
				Value operand = visit(e);
				res += types_mapping.get(operand.type) + " " + operand.toModelica() + ";\n";
				pass_args += sign.variable_names.get(args.size() - i - 1) + " =" + operand.toModelica() + ", ";
				val += f_name + "[i]." + sign.variable_names.get(args.size() - i - 1) + " =" + operand.toModelica()
						+ ";\n";
			}
		}
		current_category = previous_category; // restore

		res += val;

		// res += "out[i]:=" + sig.function_name + "(" + args_modelica + ");\n";

		res += "end for;\n";

		res += "end " + name + ";\n";

		String var_n = name + counter++;

		res += name + " " + var_n + "(" + pass_args + "ps=" + v_name + ");\n";

		localFunctionCalls.append(res);
		counter++;
		return new Value(var_n + ".out", sig.return_type, true);
	}

	private int operatorPrecedence(String op) {
		switch (op) {
			case "or":
				return 1;
			case "and":
				return 2;
			case "<":
			case "<=":
			case ">":
			case ">=":
			case "==":
			case "<>":
				return 3;
			case "+":
			case "-":
				return 4;
			case "*":
			case "/":
			case "mod":
				return 5;
			case "^":
				return 6;
			default:
				return 7;
		}
	}

	// Recursively flatten the left spine of a left-associative binary parse tree
	// into a linear list of operands (vals) and operators (ops), preserving the
	// original left-to-right token order needed for precedence re-evaluation.
	private void flattenBinaryChain(crmlParser.ExpContext ctx,
			List<crmlParser.ExpContext> vals, List<String> ops) {
		if (ctx.binary != null) {
			flattenBinaryChain(ctx.left, vals, ops);
			String op = ctx.builtin_op().getText();
			if (current_category != null) {
				String mapped = category_map.getCategory(current_category).get(op);
				if (mapped != null)
					op = mapped;
			}
			ops.add(op);
			vals.add(ctx.right);
		} else {
			vals.add(ctx);
		}
	}

	// Shunting-yard evaluation: re-applies correct operator precedence over a
	// flat (ops, vals) chain that was parsed with uniform left-associativity.
	private Value evaluateWithPrecedence(List<String> ops, List<crmlParser.ExpContext> vals) {
		for (crmlParser.ExpContext val : vals)
			if (val.iterator() != null)
				throw new ParseCancellationException("iterators need to be implemented");

		List<Value> valueStack = new ArrayList<>();
		List<String> opStack = new ArrayList<>();

		valueStack.add(visit(vals.get(0)));

		for (int i = 0; i < ops.size(); i++) {
			String op = ops.get(i);
			while (!opStack.isEmpty() &&
					operatorPrecedence(opStack.get(opStack.size() - 1)) >= operatorPrecedence(op)) {
				String topOp = opStack.remove(opStack.size() - 1);
				Value r = valueStack.remove(valueStack.size() - 1);
				Value l = valueStack.remove(valueStack.size() - 1);
				valueStack.add(apply_binary_op(topOp, l, r));
			}
			opStack.add(op);
			valueStack.add(visit(vals.get(i + 1)));
		}

		while (!opStack.isEmpty()) {
			String topOp = opStack.remove(opStack.size() - 1);
			Value r = valueStack.remove(valueStack.size() - 1);
			Value l = valueStack.remove(valueStack.size() - 1);
			valueStack.add(apply_binary_op(topOp, l, r));
		}

		return valueStack.get(0);
	}

	private Value apply_binary_op(String op, Value left, Value right) {

		// Periods while Boolean → new Periods restricted to when the Boolean is true.
		// Generates inline clock objects from the intersection condition, consistent
		// with how all other Periods constructors work in this compiler.
		if (op.equals("while") && left.type.equals("Periods") && right.type.equals("Boolean")) {
			String clockType = types_mapping.get("Clock");
			String periodsType = types_mapping.get("Periods");
			int n = counter++;
			String cStart = "c_while_start_" + n;
			String cClose = "c_while_close_" + n;
			String psWhile = "ps_while_" + n;
			String p = left.toModelica();
			String b = right.toModelica();
			localFunctionCalls.append(clockType + " " + cStart +
					"(b = CRMLtoModelica.Functions.and4(" + p + ".start_event.b, " + b + "));\n");
			localFunctionCalls
					.append("CRMLtoModelica.Types.CRMLClock_build " + cStart + "_init(clock = " + cStart + ");\n");
			localFunctionCalls.append(clockType + " " + cClose +
					"(b = CRMLtoModelica.Functions.or4(" + p + ".close_event.b, CRMLtoModelica.Functions.not4(" + b
					+ ")));\n");
			localFunctionCalls
					.append("CRMLtoModelica.Types.CRMLClock_build " + cClose + "_init(clock = " + cClose + ");\n");
			localFunctionCalls.append(periodsType + " " + psWhile +
					"(isLeftBoundaryIncluded = true, isRightBoundaryIncluded = false" +
					", start_event = " + cStart + ", close_event = " + cClose + ");\n");
			localFunctionCalls
					.append("CRMLtoModelica.Types.CRMLPeriods_build " + psWhile + "_init(ps = " + psWhile + ");\n");
			return new Value(psWhile, "Periods");
		}

		// check if predefined operator maps to Modelica operator

		Signature op_t = OperatorMapping.is_defined(operators_map, op, normalizeType(left.type),
				normalizeType(right.type), left.isSet, right.isSet);

		if (op_t == null)
			throw new ParseCancellationException(
					"Built in operator undefined : " + op + " on " + left.type + " and " + right.type + '\n');

		if (op_t.mtype == Signature.Type.OPERATOR) { // check if predefined operator maps to Modelica operator

			// special case if the return is boolean and needs to be wrapped in a CRML
			// boolean
			if (op_t.return_type.equals("Boolean"))
				return new Value("CRMLtoModelica.Functions.cvBooleanToBoolean4(" + left.toModelica() + " " + op + " "
						+ right.toModelica() + ")", "Boolean");

			return new Value(left.toModelica() + " " + op_t.function_name + " " + right.toModelica(), op_t.return_type,
					op_t.is_return_set);
		} else if (op_t.mtype == Signature.Type.FUNCTION) {
			return new Value(op_t.function_name + "(" + right.toModelica() + ", " + left.toModelica() + ")",
					op_t.return_type, op_t.is_return_set);

		} else if (op_t.mtype == Signature.Type.SET_OP) { // generate a set operator

			String block_name = op_t.function_name + "int" + counter;
			String block_type = op_t.function_name.replace(".", "_") + counter++;
			StringBuffer set_block = new StringBuffer("model " + block_type + "\n ");
			StringBuffer for_loops = new StringBuffer("");
			StringBuffer for_loop_exp = new StringBuffer("");

			Signature singular_op = OperatorMapping.is_defined(operators_map, op, left.type, right.type, false, false);
			if (singular_op == null)
				throw new ParseCancellationException("Cannot apply operator to set elements : " + op + " on "
						+ left.type + " and " + right.type + '\n');

			if (left.isSet && right.isSet) {

			} else if (left.isSet) {

				for (int i = 0; i < left.setPath.size() - 1; i++) {
					for_loops.append("for i" + i + " in " + left.setPath + "loop");
					// for_loop_exp.append(left.setPath[i] + "[i"+i+"]");
				}
			} else {

			}

			set_block.append("end " + block_type + ";\n");

			localFunctionCalls.append(set_block);

			return new Value(block_name + ".out", op_t.return_type, op_t.is_return_set);
		}
		// operator translates to block instantiation
		String name = op_t.temp_var_name.replace('.', '_') + counter;
		System.out.println("VALUES: " + op_t.variable_names.get(0) + " " + op_t.function_name);

		String res = op_t.function_name + " " + name + "(" + op_t.variable_names.get(0) + " = " + left.toModelica()
				+ ","
				+ op_t.variable_names.get(1) + " = " + right.toModelica() + ");\n";

		localFunctionCalls.append(res);
		counter++;

		return new Value(name + ".out", op_t.return_type, op_t.is_return_set);

	}

	@Override
	public Value visitConstant(crmlParser.ConstantContext ctx) {

		// TODO fix proper type conversion

		if (ctx.boolean_value() != null)
			return visit(ctx.boolean_value());

		if (ctx.string() != null)
			return new Value(ctx.string().getText(), "String");

		if (ctx.number() != null)
			if (ctx.number().getText().contains("."))
				return new Value(ctx.number().getText(), "Real");
			else
				return new Value(ctx.number().getText(), "Integer");

		if (ctx.time() != null)
			return new Value("time", "Real");

		String val = ctx.getText();

		throw new ParseCancellationException("Unable to evaluate constant " + val + '\n');
	}

	@Override
	public Value visitBoolean_value(crmlParser.Boolean_valueContext ctx) {
		String bool_val;

		bool_val = ctx.getText();

		switch (bool_val) {
			case "true":
				return new Value("CRMLtoModelica.Types.Boolean4.true4", "Boolean");
			case "false":
				return new Value("CRMLtoModelica.Types.Boolean4.false4", "Boolean");
			case "undecided":
				return new Value("CRMLtoModelica.Types.Boolean4.undecided", "Boolean");
			case "undefined":
				return new Value("CRMLtoModelica.Types.Boolean4.undefined", "Boolean");
			default:
				logger.error("Not a valid value for a boolean");
				throw new ParseCancellationException("Not a valid value for a boolean " + ctx.getText() + '\n');

		}

	}

	public void dump_userOperators() {
		for (Entry<String, Signature> e : user_operators.entrySet()) {
			System.out.println("operator " + e.getKey() + "\n");
		}
	}

	private String visitLibrary(String name) {
		if (loadedLibraries.contains(name))
			return "";
		loadedLibraries.add(name);

		InputStream is = tryOpenLibrary(name);
		if (is == null)
			throw new ParseCancellationException("Library not found: " + name);

		crmlParser.DefinitionContext libDef;
		try {
			libDef = (crmlParser.DefinitionContext) new Parser().parse(CharStreams.fromStream(is)).ast();
		} catch (IOException e) {
			throw new ParseCancellationException("Error loading library " + name + ": " + e.getMessage());
		}

		StringBuffer libBuffer = new StringBuffer();
		for (DependencyContext d : libDef.dependency()) {
			for (IdContext libId : d.id()) {
				libBuffer.append(visitLibrary(libId.getText()));
			}
		}
		for (Element_defContext e : libDef.element_def()) {
			// Save visitor state so a failed element doesn't corrupt subsequent ones.
			StringBuffer savedLocalFunctionCalls = new StringBuffer(localFunctionCalls);
			int savedCounter = counter;
			try {
				libBuffer.append(visit(e).toModelica());
			} catch (ParseCancellationException ex) {
				localFunctionCalls = savedLocalFunctionCalls;
				counter = savedCounter;
				variableTable.cleanLocalVariables();
				logger.warn("Skipping library element in " + name + " (unsupported syntax): " + ex.getMessage());
			}
		}
		return libBuffer.toString();
	}

	private InputStream tryOpenLibrary(String name) {
		try {
			InputStream is = null;
			try {
				is = Files.newInputStream(SafeResource.get("crml-libs/" + name + ".crml"));
			} catch (IllegalArgumentException e) {
			}
			if (is != null)
				return is;
			return Files.newInputStream(SafeResource.get("crml-libs/" + name.replace('_', '-') + ".crml"));
		} catch (IOException e) {
			return null;
		}
	}
}
