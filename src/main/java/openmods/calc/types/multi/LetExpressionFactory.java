package openmods.calc.types.multi;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import java.util.List;
import openmods.calc.BinaryOperator;
import openmods.calc.Environment;
import openmods.calc.ExecutionErrorException;
import openmods.calc.Frame;
import openmods.calc.FrameFactory;
import openmods.calc.ICallable;
import openmods.calc.IExecutable;
import openmods.calc.ISymbol;
import openmods.calc.SymbolCall;
import openmods.calc.SymbolMap;
import openmods.calc.Value;
import openmods.calc.parsing.ICompilerState;
import openmods.calc.parsing.IExprNode;
import openmods.calc.parsing.ISymbolCallStateTransition;
import openmods.calc.parsing.SameStateSymbolTransition;
import openmods.calc.parsing.SymbolCallNode;
import openmods.utils.OptionalInt;
import openmods.utils.Stack;

public class LetExpressionFactory {

	private final TypeDomain domain;
	private final TypedValue nullValue;
	private final BinaryOperator<TypedValue> colonOperator;
	private final BinaryOperator<TypedValue> assignOperator;

	public LetExpressionFactory(TypeDomain domain, TypedValue nullValue, BinaryOperator<TypedValue> colonOperator, BinaryOperator<TypedValue> assignOperator) {
		this.domain = domain;
		this.nullValue = nullValue;
		this.colonOperator = colonOperator;
		this.assignOperator = assignOperator;
	}

	private class LetNode extends ScopeModifierNode {
		public LetNode(String letSymbol, IExprNode<TypedValue> argsNode, IExprNode<TypedValue> codeNode) {
			super(domain, letSymbol, colonOperator, assignOperator, argsNode, codeNode);
		}

		@Override
		protected void flattenNameAndValue(List<IExecutable<TypedValue>> output, IExprNode<TypedValue> name, IExprNode<TypedValue> value) {
			if (name instanceof SymbolCallNode) {
				// f(x, y):<some code> -> f:(x,y)-><some code>
				final SymbolCallNode<TypedValue> callNode = (SymbolCallNode<TypedValue>)name;
				output.add(Value.create(Symbol.get(domain, callNode.symbol())));
				output.add(Value.create(createLambdaWrapperCode(callNode, value)));
			} else {
				// f:<some code>, 'f':<some code>, #f:<some code>
				output.add(Value.create(TypedCalcUtils.extractNameFromNode(domain, name)));
				output.add(flattenExprToCodeConstant(value));
			}
		}

		private TypedValue createLambdaWrapperCode(SymbolCallNode<TypedValue> callNode, IExprNode<TypedValue> value) {
			final List<IExecutable<TypedValue>> result = Lists.newArrayList();

			final List<TypedValue> argNames;
			try {
				argNames = extractArgNames(callNode.getChildren());
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Cannot extract lambda arg names from " + callNode);
			}
			result.add(Value.create(Cons.createList(argNames, nullValue)));
			result.add(flattenExprToCodeConstant(value));
			result.add(new SymbolCall<TypedValue>(TypedCalcConstants.SYMBOL_CLOSURE, 2, 1));
			return Code.wrap(domain, result);
		}

		private List<TypedValue> extractArgNames(Iterable<IExprNode<TypedValue>> children) {
			final List<TypedValue> result = Lists.newArrayList();
			for (IExprNode<TypedValue> child : children)
				result.add(TypedCalcUtils.extractNameFromNode(domain, child));
			return result;
		}

		private IExecutable<TypedValue> flattenExprToCodeConstant(IExprNode<TypedValue> code) {
			return Value.create(Code.flattenAndWrap(domain, code));
		}
	}

	private class LetStateTransition extends SameStateSymbolTransition<TypedValue> {
		private final String letState;

		public LetStateTransition(String letState, ICompilerState<TypedValue> parentState) {
			super(parentState);
			this.letState = letState;
		}

		@Override
		public IExprNode<TypedValue> createRootNode(List<IExprNode<TypedValue>> children) {
			Preconditions.checkState(children.size() == 2, "Expected two args for 'let' expression");
			return new LetNode(letState, children.get(0), children.get(1));
		}
	}

	public ISymbolCallStateTransition<TypedValue> createLetStateTransition(ICompilerState<TypedValue> parentState) {
		return new LetStateTransition(TypedCalcConstants.SYMBOL_LET, parentState);
	}

	public ISymbolCallStateTransition<TypedValue> createLetSeqStateTransition(ICompilerState<TypedValue> parentState) {
		return new LetStateTransition(TypedCalcConstants.SYMBOL_LETSEQ, parentState);
	}

	public ISymbolCallStateTransition<TypedValue> createLetRecStateTransition(ICompilerState<TypedValue> parentState) {
		return new LetStateTransition(TypedCalcConstants.SYMBOL_LETREC, parentState);
	}

	private static class PlaceholderSymbol implements ISymbol<TypedValue> {
		@Override
		public void call(Frame<TypedValue> frame, OptionalInt argumentsCount, OptionalInt returnsCount) {
			throw new ExecutionErrorException("Cannot call symbol during definition");
		}

		@Override
		public TypedValue get() {
			throw new ExecutionErrorException("Cannot reference symbol during definition");
		}
	}

	@SuppressWarnings("serial")
	private static class InvalidArgsException extends RuntimeException {}

	private static abstract class LetSymbolBase implements ICallable<TypedValue> {

		@Override
		public void call(Frame<TypedValue> currentFrame, OptionalInt argumentsCount, OptionalInt returnsCount) {
			TypedCalcUtils.expectExactArgCount(argumentsCount, 2);

			final Frame<TypedValue> letFrame = FrameFactory.newLocalFrameWithSubstack(currentFrame, 2);
			final Stack<TypedValue> letStack = letFrame.stack();
			final Code code = letStack.pop().as(Code.class, "second (code) 'let' parameter");
			final Cons vars = letStack.pop().as(Cons.class, "first (var list) 'let'  parameter");

			try {
				prepareFrame(letFrame.symbols(), currentFrame.symbols(), vars);
			} catch (InvalidArgsException e) {
				throw new IllegalArgumentException("Expected list of name:value pairs on second 'let' parameter, got " + vars, e);
			}

			code.execute(letFrame);

			TypedCalcUtils.expectExactReturnCount(returnsCount, letStack.size());
		}

		protected abstract void prepareFrame(SymbolMap<TypedValue> outputFrame, SymbolMap<TypedValue> callSymbols, Cons vars);
	}

	private abstract class ArgPairVisitor extends Cons.ListVisitor {
		public ArgPairVisitor() {
			super(nullValue);
		}

		@Override
		public void value(TypedValue value, boolean isLast) {
			if (!value.is(Cons.class)) throw new InvalidArgsException();
			final Cons pair = value.as(Cons.class);

			Preconditions.checkState(pair.car.is(Symbol.class));
			final Symbol name = pair.car.as(Symbol.class);

			if (!pair.cdr.is(Code.class)) throw new InvalidArgsException();
			final Code valueExpr = pair.cdr.as(Code.class);

			acceptVar(name, valueExpr);
		}

		protected abstract void acceptVar(Symbol name, Code value);

		@Override
		public void end(TypedValue terminator) {}

		@Override
		public void begin() {}
	}

	private class LetSymbol extends LetSymbolBase {
		@Override
		protected void prepareFrame(final SymbolMap<TypedValue> outputSymbols, final SymbolMap<TypedValue> callSymbols, Cons vars) {
			vars.visit(new ArgPairVisitor() {
				@Override
				protected void acceptVar(Symbol name, Code expr) {
					final Frame<TypedValue> executionFrame = FrameFactory.newLocalFrame(callSymbols);
					executionFrame.symbols().put(name.value, new PlaceholderSymbol());

					expr.execute(executionFrame);

					final Stack<TypedValue> resultStack = executionFrame.stack();
					Preconditions.checkState(resultStack.size() == 1, "Expected single result from 'let' expression, got %s", resultStack.size());
					final TypedValue result = resultStack.pop();
					executionFrame.symbols().put(name.value, result); // replace placeholder with actual value
					outputSymbols.put(name.value, result);
				}
			});
		}
	}

	private class LetSeqSymbol extends LetSymbolBase {
		@Override
		protected void prepareFrame(final SymbolMap<TypedValue> outputSymbols, SymbolMap<TypedValue> callSymbols, Cons vars) {
			final Frame<TypedValue> executionFrame = FrameFactory.symbolsToFrame(outputSymbols);
			final Stack<TypedValue> resultStack = executionFrame.stack();

			vars.visit(new ArgPairVisitor() {
				@Override
				protected void acceptVar(Symbol name, Code expr) {
					outputSymbols.put(name.value, new PlaceholderSymbol());
					expr.execute(executionFrame);
					Preconditions.checkState(resultStack.size() == 1, "Expected single result from 'let' expression, got %s", resultStack.size());
					outputSymbols.put(name.value, resultStack.pop());
				}
			});
		}
	}

	private static class NameCodePair {
		public final String name;
		public final Code code;

		public NameCodePair(String name, Code code) {
			this.name = name;
			this.code = code;
		}
	}

	private static class NameValuePair {
		public final String name;
		public final TypedValue value;

		public NameValuePair(String name, TypedValue value) {
			this.name = name;
			this.value = value;
		}

	}

	private class LetRecSymbol extends LetSymbolBase {
		@Override
		protected void prepareFrame(final SymbolMap<TypedValue> outputSymbols, SymbolMap<TypedValue> callSymbols, Cons vars) {
			final Frame<TypedValue> executionFrame = FrameFactory.newLocalFrame(callSymbols);
			final SymbolMap<TypedValue> executionSymbols = executionFrame.symbols();
			final List<NameCodePair> varsToExecute = Lists.newArrayList();

			// fill placeholders, collect data
			vars.visit(new ArgPairVisitor() {
				@Override
				protected void acceptVar(Symbol name, Code expr) {
					executionSymbols.put(name.value, new PlaceholderSymbol());
					varsToExecute.add(new NameCodePair(name.value, expr));
				}
			});

			final Stack<TypedValue> resultStack = executionFrame.stack();

			// evaluate expressions
			final List<NameValuePair> varsToSet = Lists.newArrayList();
			for (NameCodePair e : varsToExecute) {
				e.code.execute(executionFrame);
				Preconditions.checkState(resultStack.size() == 1, "Expected single result from 'let' expression, got %s", resultStack.size());
				final TypedValue result = resultStack.pop();
				varsToSet.add(new NameValuePair(e.name, result));
			}

			// expose results to namespace - must be done after evaluations, since all symbols must be executed with dummy values in place
			// IMO this is more consistent than "each id is initialized immediately after the corresponding val-expr is evaluated"
			for (NameValuePair e : varsToSet) {
				executionSymbols.put(e.name, e.value);
				outputSymbols.put(e.name, e.value);
			}
		}
	}

	public void registerSymbol(Environment<TypedValue> env) {
		env.setGlobalSymbol(TypedCalcConstants.SYMBOL_LET, new LetSymbol());
		env.setGlobalSymbol(TypedCalcConstants.SYMBOL_LETSEQ, new LetSeqSymbol());
		env.setGlobalSymbol(TypedCalcConstants.SYMBOL_LETREC, new LetRecSymbol());
	}
}
