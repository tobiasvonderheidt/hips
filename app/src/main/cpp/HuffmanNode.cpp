#include "HuffmanNode.h"

HuffmanNode::HuffmanNode(
    llama_token token,
    double probability,
    HuffmanNode* left,
    HuffmanNode* right
)
: token(token), probability(probability), left(left), right(right)
{ }
