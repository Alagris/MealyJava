package net.alagris;

import java.awt.event.KeyEvent;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Sequence of integers implementation
 */
public final class IntSeq implements Seq<Integer>, Comparable<IntSeq>, List<Integer> {

	public static final IntSeq Epsilon = new IntSeq();

	private final int[] arr;
	private final int endExclusive;
	private final int offset;

	public IntSeq(CharSequence s) {
		this(s.codePoints().toArray());
	}

	public IntSeq(int... arr) {
		this(arr, 0, arr.length);
	}
	public IntSeq(int beginInclusive, int endExclusive, int[] arr) {
		this.arr = arr;
		this.offset = beginInclusive;
		this.endExclusive = endExclusive;
		assert offset <= arr.length : offset + " <= " + arr.length;
		assert endExclusive <= arr.length : endExclusive + " <= " + arr.length;
		assert this.endExclusive <= arr.length : this.endExclusive + " <= " + arr.length;
		assert 0 <= offset;
		assert endExclusive <= arr.length;
		assert 0 <= endExclusive;
	}
	public IntSeq(int[] arr, int offset, int size) {
		this.arr = arr;
		this.offset = offset;
		this.endExclusive = offset + size;
		assert offset <= arr.length : offset + " <= " + arr.length;
		assert offset + size <= arr.length : (offset + size) + " <= " + arr.length;
		assert endExclusive <= arr.length : endExclusive + " <= " + arr.length;
		assert 0 <= offset;
		assert endExclusive <= arr.length;
		assert 0 <= endExclusive;
	}

	public static IntSeq rand(int lenFromInclusive,int lenToExclusive,int minIntInclusive,int maxIntExclusive,Random rnd) {
		return rand(rnd.nextInt(lenToExclusive-lenFromInclusive)+lenFromInclusive,minIntInclusive,maxIntExclusive,rnd);
	}
    public static IntSeq rand(int len,int minIntInclusive,int maxIntExclusive,Random rnd) {
		final int[] arr = new int[len];
		for(int i=0;i<arr.length;i++){
			arr[i]=minIntInclusive+rnd.nextInt(maxIntExclusive-minIntInclusive);
			assert minIntInclusive<=arr[i]&&arr[i]<maxIntExclusive:minIntInclusive+" <= "+arr[i]+" < "+maxIntExclusive;
		}
 		return new IntSeq(arr);
    }

	public static void appendCodepointRange(StringBuilder sb, int fromInclusive, int toInclusive) {
		if (fromInclusive == toInclusive) {
			sb.append("<");
			sb.append(fromInclusive);
			sb.append(">");
		} else {
			sb.append("<");
			sb.append(fromInclusive);
			sb.append("-");
			sb.append(toInclusive);
			sb.append(">");
		}
	}
	public static void appendRange(StringBuilder sb, int fromInclusive, int toInclusive) {
		if (isPrintableChar(fromInclusive) && isPrintableChar(toInclusive)) {
			if (fromInclusive == toInclusive) {
				sb.append("'");
				IntSeq.appendPrintableChar(sb, fromInclusive);
				sb.append("'");
			} else {
				sb.append("[");
				IntSeq.appendPrintableChar(sb, fromInclusive);
				sb.append("-");
				IntSeq.appendPrintableChar(sb, toInclusive);
				sb.append("]");
			}
		} else {
			appendCodepointRange(sb,fromInclusive,toInclusive);
		}
	}

	@Override
	public int size() {
		return endExclusive - offset;
	}

	@Override
	public boolean contains(Object o) {
		return indexOf(o) > -1;
	}

	@Override
	public Integer get(int i) {
		return at(i);
	}

	public int at(int i) {
		return arr[offset + i];
	}

	private int hash = 0;

	@Override
	public int hashCode() {
		if (hash == 0) {
			int result = 1;
			for (int i = offset; i < endExclusive; i++)
				result = 31 * result + arr[i];
			hash = result;
		}
		return hash;
	}

	@Override
	public boolean isEmpty() {
		return size() == 0;
	}

	@Override
	public Integer set(int index, Integer element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void add(int index, Integer element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Integer remove(int index) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int indexOf(Object o) {
		int j = (int) o;
		int i = offset;
		while (i < size())
			if (arr[i] == j)
				return i - offset;
			else
				i++;
		return -1;
	}

	@Override
	public int lastIndexOf(Object o) {
		int j = (int) o;
		int i = endExclusive;
		while (--i >= 0)
			if (arr[i] == j)
				return i - offset;
		return -1;
	}

	@Override
	public ListIterator<Integer> listIterator() {
		return listIterator(0);
	}

	@Override
	public ListIterator<Integer> listIterator(int index) {
		return new ListIterator<Integer>() {
			int i = index + offset;

			@Override
			public boolean hasNext() {
				return i < endExclusive;
			}

			@Override
			public Integer next() {
				return arr[i++];
			}

			@Override
			public boolean hasPrevious() {
				return i > offset;
			}

			@Override
			public Integer previous() {
				return arr[i--];
			}

			@Override
			public int nextIndex() {
				return i + 1;
			}

			@Override
			public int previousIndex() {
				return i - 1;
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}

			@Override
			public void set(Integer integer) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void add(Integer integer) {
				throw new UnsupportedOperationException();
			}
		};
	}

	@Override
	public List<Integer> subList(int fromIndex, int toIndex) {
		final int from = fromIndex + offset;
		final int to = offset + toIndex;
		assert to <= endExclusive;
		assert from < endExclusive;
		return new IntSeq(arr, from, to);
	}

	@Override
	public Spliterator<Integer> spliterator() {
		return subList(0, size()).spliterator();
	}

	@Override
	public Stream<Integer> stream() {
		return Arrays.stream(arr, offset, endExclusive).boxed();
	}

	@Override
	public Stream<Integer> parallelStream() {
		return subList(0, size()).parallelStream();
	}

	public IntSeq concat(IntSeq rhs) {
		int[] n = new int[size() + rhs.size()];
		System.arraycopy(arr, offset, n, 0, size());
		System.arraycopy(rhs.arr, rhs.offset, n, size(), rhs.size());
		return new IntSeq(n);
	}

	@Override
	public boolean equals(Object obj) {
		IntSeq rhs = (IntSeq) obj;
		if (rhs.size() != size())
			return false;
		for (int i = 0; i < size(); i++) {
			if (at(i) != rhs.at(i))
				return false;
		}
		return true;
	}

	@Override
	public Iterator<Integer> iterator() {
		return new Iterator<Integer>() {
			int i = offset;

			@Override
			public boolean hasNext() {
				return i < endExclusive;
			}

			@Override
			public Integer next() {
				return arr[i++];
			}
		};
	}

	public Iterator<Integer> iter(int offset) {
		return new Iterator<Integer>() {
			int i = IntSeq.this.offset+offset;

			@Override
			public boolean hasNext() {
				return i < endExclusive;
			}

			@Override
			public Integer next() {
				return arr[i++];
			}
		};
	}

	@Override
	public void forEach(Consumer<? super Integer> action) {
		for (int i = offset; i < endExclusive; i++)
			action.accept(arr[i]);
	}

	@Override
	public Object[] toArray() {
		return Arrays.stream(arr, offset, endExclusive).boxed().toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		Integer[] e = new Integer[size()];
		for (int i = offset; i < endExclusive; i++)
			e[i] = arr[i];
		return (T[]) e;
	}

	@Override
	public boolean add(Integer integer) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		Collection<Integer> ic = (Collection<Integer>) c;
		for (int i : ic) {
			if (!contains(i))
				return false;
		}
		return true;
	}

	@Override
	public boolean addAll(Collection<? extends Integer> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean addAll(int index, Collection<? extends Integer> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeIf(Predicate<? super Integer> filter) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void replaceAll(UnaryOperator<Integer> operator) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void sort(Comparator<? super Integer> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String toString() {
        return toStringLiteral();
    }

    public String toUnicodeString() {
        return new String(arr, offset, size());
    }

    public String toCodepointString(){
        if (size() == 0) return "[]";
		StringBuilder b = new StringBuilder("[");
		b.append(arr[offset]);
		for (int i = offset + 1; i < endExclusive; i++) {
			b.append(", ").append(arr[i]);
		}
		b.append("]");
		return b.toString();
	}
	@Override
	public int compareTo(IntSeq other) {
		int len1 = size();
		int len2 = other.size();
		int lim = Math.min(len1, len2);
		for (int k = 0; k < lim; k++) {
			int c1 = at(k);
			int c2 = other.at(k);
			if (c1 != c2) {
				return c1 - c2;
			}
		}
		return len1 - len2;
	}

	public int lexLenCompareTo(IntSeq other) {
		int len1 = size();
		int len2 = other.size();
		if (len1 < len2)
			return -1;
		if (len1 > len2)
			return 1;
		for (int k = 0; k < len1; k++) {
			int c1 = at(k);
			int c2 = other.at(k);
			if (c1 != c2) {
				return c1 - c2;
			}
		}
		return 0;
	}



	public Iterator<Integer> iteratorReversed() {
		return new Iterator<Integer>() {
			int i = size();

			@Override
			public boolean hasNext() {
				return i > 0;
			}

			@Override
			public Integer next() {
				return arr[--i];
			}
		};
	}

	public int lcp(IntSeq second) {
		final int minLen = Math.min(size(), second.size());
		int lcp = 0;
		while (lcp < minLen && at(lcp) == second.at(lcp)) {
			lcp++;
		}
		return lcp;
	}

	public IntSeq sub(int fromInclusive) {
		return new IntSeq(arr, offset + fromInclusive, size() - fromInclusive);
	}
	public IntSeq sub(int fromInclusive, int endExclusive) {
		return new IntSeq(arr, offset + fromInclusive, offset + endExclusive);
	}

	public static boolean isPrintableChar(int c) {
		if(c == '\n' || c == '\t' ||c=='\r'|| c == '\0' || c == ' ' || c == '\b') return true;
		try {
			Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
			return (!Character.isISOControl(c)) && c != KeyEvent.CHAR_UNDEFINED && block != null
					&& block != Character.UnicodeBlock.SPECIALS;
		}catch (IllegalArgumentException e) {
			return false;
		}
	}
	public static StringBuilder appendPrintableChar(StringBuilder sb, int c) {
		switch (c) {
		case '\n':
			sb.append("\\n");
			break;
		case '\r':
			sb.append("\\r");
			break;
		case '\'':
			sb.append("\\'");
			break;
		case '\t':
			sb.append("\\t");
			break;
		case '\b':
			sb.append("\\b");
			break;
		case '\0':
			sb.append("\\0");
			break;
		default:
			sb.appendCodePoint(c);
			break;
		}
		return sb;
	}
	public String toStringLiteral() {
		boolean isPrintable = true;
		for (int i = offset; i < endExclusive; i++) {
			if (!isPrintableChar(arr[i])) {
				isPrintable = false;
			}
		}
		final StringBuilder sb = new StringBuilder();
		if (isPrintable) {
			sb.append("'");
			for (int i = offset; i < endExclusive; i++) {
				appendPrintableChar(sb, arr[i]);
			}
			sb.append("'");
		}else {
			sb.append("<");
			assert offset < endExclusive;
			sb.append(arr[offset]);
			for (int i = offset+1; i < endExclusive; i++) {
				sb.append(' ').append(arr[i]);	
			}
			sb.append(">");
		}
		return sb.toString();
	}

    public boolean isPrefixOf(int offsetBoth, IntSeq other) {
		assert offsetBoth<size();
		if(size()>other.size())return false;
		for(int i=offsetBoth;i<size();i++){
			if(at(i)!=other.at(i))return false;
		}
		return true;
    }

    /**Use it only if you are sure that this IntSeq is not used anywhere else.*/
	public int[] unsafe() {
		return arr;
	}

    public IntSeq copy() {
		return new IntSeq(Arrays.copyOfRange(arr,offset,endExclusive));
    }
}
