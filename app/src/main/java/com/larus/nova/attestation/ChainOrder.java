package com.larus.nova.attestation;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** Order one selected path, never concatenate branches. Equality must identify full certificates. */
public final class ChainOrder {
    private ChainOrder() {}
    public static <T> List<T> select(List<T> input, T preferredLeaf,
                                     BiPredicate<T, T> signedBy, Predicate<T> selfSigned) {
        List<T> unique = new ArrayList<>(new LinkedHashSet<>(input));
        if (unique.isEmpty()) throw new IllegalArgumentException("Empty certificate set");
        if (unique.size() > 128) throw new IllegalArgumentException("Too many certificates");
        T leaf = preferredLeaf;
        if (leaf != null && !unique.contains(leaf)) throw new IllegalArgumentException("Selected leaf is missing");
        if (leaf == null) {
            List<T> leaves = new ArrayList<>();
            for (T candidate : unique) {
                boolean parent = false;
                for (T child : unique) {
                    if (!candidate.equals(child) && signedBy.test(child, candidate)) { parent = true; break; }
                }
                if (!parent) leaves.add(candidate);
            }
            if (leaves.size() != 1) throw new IllegalArgumentException(
                "Multiple independent certificate paths; import one chain at a time / 请分别导入独立证书链");
            leaf = leaves.get(0);
        }
        List<T> path = new ArrayList<>();
        Set<T> seen = new HashSet<>();
        T current = leaf;
        while (true) {
            if (!seen.add(current)) throw new IllegalArgumentException("Certificate chain cycle");
            path.add(current);
            if (selfSigned.test(current)) break;
            List<T> parents = new ArrayList<>();
            for (T candidate : unique) {
                if (!candidate.equals(current) && signedBy.test(current, candidate)) parents.add(candidate);
            }
            if (parents.size() != 1) throw new IllegalArgumentException(parents.isEmpty()
                ? "Missing or invalid issuer/signature / 缺少发行者或证书签名无效"
                : "Ambiguous issuer certificates / 发行者证书不唯一");
            current = parents.get(0);
        }
        if (preferredLeaf == null && path.size() != unique.size()) {
            throw new IllegalArgumentException("Unrelated certificates in import / 导入包含无关证书");
        }
        return path;
    }
}
