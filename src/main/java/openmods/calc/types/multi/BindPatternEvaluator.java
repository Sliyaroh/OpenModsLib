package openmods.calc.types.multi;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.List;
import openmods.calc.Frame;
import openmods.calc.FrameFactory;
import openmods.calc.ISymbol;
import openmods.calc.NestedSymbolMap;
import openmods.calc.SingleReturnCallable;
import openmods.calc.SymbolMap;
import openmods.calc.types.multi.BindPatternTranslator.IPatternProvider;
import openmods.calc.types.multi.BindPatternTranslator.PatternPart;
import openmods.utils.OptionalInt;
import openmods.utils.Stack;

public class BindPatternEvaluator {

	private final TypeDomain domain;

	public BindPatternEvaluator(TypeDomain domain) {
		this.domain = domain;
	}

	private static class PatternAny implements PatternPart {
		public static final PatternAny INSTANCE = new PatternAny();

		@Override
		public boolean match(Frame<TypedValue> env, SymbolMap<TypedValue> output, TypedValue value) {
			return true;
		}
	}

	private static class PatternBindName implements PatternPart {
		private final String name;

		public PatternBindName(String name) {
			this.name = name;
		}

		@Override
		public boolean match(Frame<TypedValue> env, SymbolMap<TypedValue> output, TypedValue value) {
			output.put(name, value);
			return true;
		}
	}

	private static class VarPlaceholder implements IPatternProvider {
		public final String var;

		public VarPlaceholder(String var) {
			this.var = var;
		}

		@Override
		public PatternPart getPattern(BindPatternTranslator translator) {
			return var.equals(TypedCalcConstants.MATCH_ANY)
					? PatternAny.INSTANCE
					: new PatternBindName(var);
		}
	}

	private abstract static class PatternMatchConstructor implements PatternPart {
		private final List<PatternPart> argMatchers;

		public PatternMatchConstructor(List<PatternPart> argMatchers) {
			this.argMatchers = argMatchers;
		}

		@Override
		public boolean match(Frame<TypedValue> env, SymbolMap<TypedValue> output, TypedValue value) {
			final TypedValue typeValue = findConstructor(env);

			final MetaObject.SlotDecompose decomposer = typeValue.getMetaObject().slotDecompose;

			if (decomposer != null) {
				final int expectedValueCount = argMatchers.size();
				final Optional<List<TypedValue>> maybeDecomposition = decomposer.tryDecompose(typeValue, value, expectedValueCount, env);
				if (!maybeDecomposition.isPresent()) return false;

				final List<TypedValue> decomposition = maybeDecomposition.get();
				final int actualValueCount = decomposition.size();
				Preconditions.checkState(actualValueCount == expectedValueCount, "Decomposable contract broken - returned different number of values: expected: %s, got %s", expectedValueCount, actualValueCount);

				for (int i = 0; i < actualValueCount; i++) {
					final PatternPart pattern = argMatchers.get(i);
					final TypedValue var = decomposition.get(i);

					if (!pattern.match(env, output, var)) return false;
				}

				return true;
			}

			throw new IllegalStateException("Value " + typeValue + " does not describe constructor or type");
		}

		protected abstract TypedValue findConstructor(Frame<TypedValue> env);
	}

	private static class PatternMatchLocalConstructor extends PatternMatchConstructor {
		private final String typeName;

		public PatternMatchLocalConstructor(List<PatternPart> argMatchers, String typeName) {
			super(argMatchers);
			this.typeName = typeName;
		}

		@Override
		protected TypedValue findConstructor(Frame<TypedValue> env) {
			final ISymbol<TypedValue> type = env.symbols().get(typeName);
			Preconditions.checkState(type != null, "Can't find decomposable constructor %s", typeName);
			return type.get();
		}
	}

	private static class CtorPlaceholder implements IPatternProvider {
		private final String var;

		private final List<TypedValue> args;

		public CtorPlaceholder(String var, Iterable<TypedValue> args) {
			this.var = var;
			this.args = ImmutableList.copyOf(args);
		}

		@Override
		public PatternPart getPattern(BindPatternTranslator translator) {
			return new PatternMatchLocalConstructor(translatePatterns(translator, args), var);
		}
	}

	private static class PatternMatchNamespaceConstructor extends PatternMatchConstructor {
		private final String pathStart;

		private final List<String> path;

		public PatternMatchNamespaceConstructor(List<PatternPart> argMatchers, String pathStart, List<String> path) {
			super(argMatchers);
			this.pathStart = pathStart;
			this.path = path;
		}

		@Override
		protected TypedValue findConstructor(Frame<TypedValue> env) {
			final ISymbol<TypedValue> initialSymbol = env.symbols().get(pathStart);
			Preconditions.checkState(initialSymbol != null, "Can't find symbol %s", pathStart);

			TypedValue result = initialSymbol.get();

			for (String p : path) {
				final MetaObject.SlotAttr slotAttr = result.getMetaObject().slotAttr;
				Preconditions.checkState(slotAttr != null, "Value %s is not structure", result);

				final Optional<TypedValue> maybeNewResult = slotAttr.attr(result, p, env);
				Preconditions.checkState(maybeNewResult.isPresent(), "Can't find value %s in in %s", p, result);
				result = maybeNewResult.get();
			}

			return result;
		}
	}

	private static class TerminalNamespaceCtorPlaceholder implements IPatternProvider {
		private final String var;
		private final List<String> path;
		private final List<TypedValue> args;

		public TerminalNamespaceCtorPlaceholder(String var, List<String> path, Iterable<TypedValue> args) {
			this.var = var;
			this.path = path;
			this.args = ImmutableList.copyOf(args);
		}

		@Override
		public PatternPart getPattern(BindPatternTranslator translator) {
			return new PatternMatchNamespaceConstructor(translatePatterns(translator, args), var, path);
		}
	}

	private static class NamespaceCtorPlaceholder implements IPatternProvider {
		private final String var;
		private final List<String> path;

		public NamespaceCtorPlaceholder(String var, List<String> path) {
			this.var = var;
			this.path = path;
		}

		@Override
		public PatternPart getPattern(BindPatternTranslator translator) {
			throw new IllegalStateException("Unfinished namespace constructor matcher: " + var + "." + Joiner.on(".").join(path));
		}

		public IPatternProvider extend(String key) {
			final List<String> newPath = Lists.newArrayList(path);
			newPath.add(key);
			return new NamespaceCtorPlaceholder(var, newPath);
		}

		public IPatternProvider terminate(Iterable<TypedValue> args) {
			return new TerminalNamespaceCtorPlaceholder(var, path, args);
		}
	}

	private static List<PatternPart> translatePatterns(BindPatternTranslator translator, List<TypedValue> args) {
		final List<PatternPart> varMatchers = Lists.newArrayList();

		for (TypedValue m : args)
			varMatchers.add(translator.translatePattern(m));

		return varMatchers;
	}

	private final MetaObject namespaceCtorPlaceholderMetaObject = MetaObject.builder()
			.set(new MetaObject.SlotAttr() {
				@Override
				public Optional<TypedValue> attr(TypedValue self, String key, Frame<TypedValue> frame) {
					final NamespaceCtorPlaceholder placeholder = (NamespaceCtorPlaceholder)self.as(IPatternProvider.class);
					return Optional.of(domain.create(IPatternProvider.class, placeholder.extend(key), self.getMetaObject()));
				}
			})
			.set(new MetaObject.SlotCall() {
				@Override
				public void call(TypedValue self, OptionalInt argumentsCount, OptionalInt returnsCount, Frame<TypedValue> frame) {
					Preconditions.checkArgument(argumentsCount.isPresent(), "Type constructor must be always called with arg count");
					TypedCalcUtils.expectSingleReturn(returnsCount);

					final NamespaceCtorPlaceholder placeholder = (NamespaceCtorPlaceholder)self.as(IPatternProvider.class);
					final Stack<TypedValue> stack = frame.stack().substack(argumentsCount.get());
					final IPatternProvider terminalPlaceholder = placeholder.terminate(stack);
					stack.clear();
					stack.push(domain.create(IPatternProvider.class, terminalPlaceholder));
				}

			})
			.build();

	private TypedValue createCtorPlaceholder(String name, Frame<TypedValue> frame, OptionalInt argumentsCount) {
		Preconditions.checkArgument(argumentsCount.isPresent(), "Type constructor must be always called with arg count");
		final Stack<TypedValue> stack = frame.stack().substack(argumentsCount.get());
		final CtorPlaceholder placeholder = new CtorPlaceholder(name, stack);
		stack.clear();
		return domain.create(IPatternProvider.class, placeholder);
	}

	private final MetaObject varPlaceholderMetaObject = MetaObject.builder()
			.set(new MetaObject.SlotAttr() {
				@Override
				public Optional<TypedValue> attr(TypedValue self, String key, Frame<TypedValue> frame) {
					final VarPlaceholder placeholder = (VarPlaceholder)self.as(IPatternProvider.class);

					return Optional.of(domain.create(IPatternProvider.class,
							new NamespaceCtorPlaceholder(placeholder.var, ImmutableList.of(key)),
							namespaceCtorPlaceholderMetaObject));
				}
			})
			.set(new MetaObject.SlotCall() {
				@Override
				public void call(TypedValue self, OptionalInt argumentsCount, OptionalInt returnsCount, Frame<TypedValue> frame) {
					final VarPlaceholder placeholder = (VarPlaceholder)self.as(IPatternProvider.class);

					TypedCalcUtils.expectSingleReturn(returnsCount);
					frame.stack().push(createCtorPlaceholder(placeholder.var, frame, argumentsCount));
				}

			})
			.build();

	private class PlaceholderSymbol extends SingleReturnCallable<TypedValue> implements ISymbol<TypedValue> {
		private final String var;

		public PlaceholderSymbol(String var) {
			this.var = var;
		}

		@Override
		public TypedValue get() {
			return domain.create(IPatternProvider.class, new VarPlaceholder(var), varPlaceholderMetaObject);
		}

		@Override
		public TypedValue call(Frame<TypedValue> frame, OptionalInt argumentsCount) {
			return createCtorPlaceholder(var, frame, argumentsCount);
		}
	}

	private class PatternPlaceholdersSymbolMap extends NestedSymbolMap<TypedValue> {
		public PatternPlaceholdersSymbolMap(SymbolMap<TypedValue> parent) {
			super(parent);
		}

		@Override
		public void put(String name, ISymbol<TypedValue> symbol) {
			throw new UnsupportedOperationException("Can't create new symbols in match patterns");
		}

		@Override
		public ISymbol<TypedValue> get(String name) {
			final ISymbol<TypedValue> parentSymbol = super.get(name);
			if (parentSymbol != null) return parentSymbol;
			return new PlaceholderSymbol(name);
		}
	}

	public TypedValue evaluate(SymbolMap<TypedValue> topSymbolMap, Code pattern) {
		final Frame<TypedValue> patternFrame = FrameFactory.symbolsToFrame(new PatternPlaceholdersSymbolMap(topSymbolMap));
		pattern.execute(patternFrame);
		final Stack<TypedValue> resultStack = patternFrame.stack();
		Preconditions.checkState(resultStack.size() == 1, "Invalid result of pattern compilation");
		return resultStack.pop();
	}
}
