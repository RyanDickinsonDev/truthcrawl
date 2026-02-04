package io.truthcrawl.core;

import java.util.Collections;
import java.util.List;

/**
 * A validated, ordered sequence of chain links forming an append-only batch chain.
 *
 * <p>Validates that:
 * <ul>
 *   <li>The chain is non-empty.</li>
 *   <li>The first link is a genesis link (previous_root = 64 zeros).</li>
 *   <li>Each subsequent link's previous_root equals the prior link's merkle_root.</li>
 * </ul>
 */
public final class BatchChain {

    private final List<ChainLink> links;

    private BatchChain(List<ChainLink> links) {
        this.links = links;
    }

    /**
     * Create a validated batch chain from an ordered list of links.
     *
     * @param links ordered chain links (genesis first, head last)
     * @return a validated BatchChain
     * @throws IllegalArgumentException if validation fails
     */
    public static BatchChain of(List<ChainLink> links) {
        if (links.isEmpty()) {
            throw new IllegalArgumentException("Chain must not be empty");
        }

        ChainLink genesis = links.get(0);
        if (!genesis.isGenesis()) {
            throw new IllegalArgumentException(
                    "First link must be genesis (previous_root = all zeros), got: "
                            + genesis.previousRoot());
        }

        for (int i = 1; i < links.size(); i++) {
            ChainLink prev = links.get(i - 1);
            ChainLink curr = links.get(i);
            if (!curr.previousRoot().equals(prev.merkleRoot())) {
                throw new IllegalArgumentException(
                        "Chain broken at link " + i + ": previous_root "
                                + curr.previousRoot() + " does not match prior merkle_root "
                                + prev.merkleRoot());
            }
        }

        return new BatchChain(Collections.unmodifiableList(List.copyOf(links)));
    }

    /**
     * The genesis (first) link.
     */
    public ChainLink genesis() {
        return links.get(0);
    }

    /**
     * The head (most recent) link.
     */
    public ChainLink head() {
        return links.get(links.size() - 1);
    }

    /**
     * All links in order (genesis to head).
     */
    public List<ChainLink> links() {
        return links;
    }

    /**
     * Number of links in the chain.
     */
    public int length() {
        return links.size();
    }
}
