package consoperators;

import beast.core.Description;
import beast.core.Input;
import beast.core.parameter.RealParameter;
import beast.evolution.branchratemodel.BranchRateModel;
import beast.evolution.operators.TreeOperator;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.util.Randomizer;

import java.text.DecimalFormat;
import java.util.List;

@Description("SimpleDistance: Propose a new root time")
public class SimpleDistance extends TreeOperator {
    public final Input<Double> twindowSizeInput =
            new Input<>("twindowSize", "the size of the window for proposing new node time", Input.Validate.REQUIRED);
    final public Input<RealParameter> rateInput = new Input<>("rates", "the rates associated with nodes in the tree for sampling of individual rates among branches.", Input.Validate.REQUIRED);

    private double twindowSize;
    private RealParameter rates;


    @Override
    public void initAndValidate() {
        twindowSize = twindowSizeInput.get();
        rates = rateInput.get();
    }

    @Override
    public double proposal() {
        final Tree tree = treeInput.get(this);
        int branchCount = tree.getNodeCount() - 1; //the number of branches of the tree

        //the chosen node to work on
        Node node;

        //the original node time
        double t_x;
        double t_j;
        double t_k;

        double t_x_;
        double r_x;
        double r_i;
        double d_i;
        double d_x;


        //Step 1: get the root of the tree
        node = tree.getRoot();
        t_x = node.getHeight();//root time

        // son
        Node son = node.getChild(0);//get the left child of this node, i.e. son
        t_j = son.getHeight();//node time of son

        int nodeN02 = son.getNr();//node number of son
        if (nodeN02 == branchCount) {
            nodeN02 = son.getTree().getRoot().getNr();
        }

        r_i = rates.getValues()[nodeN02];
        d_i = r_i * (t_x - t_j);

        // daughter
        Node daughter = node.getChild(1);//get the right child of this node, i.e. daughter
        t_k = daughter.getHeight();//node time of daughter

        int nodeN03 = daughter.getNr(); // node time of daughter
        if (nodeN03 == branchCount) {
            nodeN03 = daughter.getTree().getRoot().getNr();
        }

        r_x = rates.getValues()[nodeN03];
        d_x = r_x * (t_x - t_k);

        double a = Randomizer.uniform(-twindowSize, twindowSize);
        //to propose a new node time for this node
        t_x_ = t_x + a;

        double lower = Math.max(t_j, t_k);
        if (t_x_ <= lower) {
            return Double.NEGATIVE_INFINITY;
        }
        node.setHeight(t_x_);

        //Step 3: make changes on the rates
        double r_i_ = d_i / (t_x_ - t_j);
        double r_x_ = d_x / (t_x_ - t_k);

        //Step 4: set the proposed new rates
        rates.setValue(nodeN02, r_i_);
        rates.setValue(nodeN03, r_x_);

        //Step5: calculate the Hastings ratio
        /*
         t_x_ = t_x + a
         r_i_ = r_i * (t_x - t_j) / (t_x_ - t_j)
         r_x_ = r_x * (t_x - t_k) / (t_x_ - t_k)
        double [][] J = new double[3][3];
        J[0][0] = 1;
        J[1][0] = r_i / (t_x_ - t_j);
        J[2][0] = r_x / (t_x_ - t_k);
        J[1][1] = (t_x - t_j) / (t_x_ - t_j);
        J[2][2] = (t_x - t_k) / (t_x_ - t_k);
        double Det = JD.Determinant(J,2);
         return Math.log(Det);
*/

        double nu = (t_x - t_j) * (t_x - t_k);
        double de = (t_x_ - t_j) * (t_x_ - t_k);
        double hastingsratio = nu / de;
        return Math.log(hastingsratio);
    }

    /**
     * automatic parameter tuning *
     */
    @Override
    public double getCoercableParameterValue() {
        return twindowSize;
    }

    @Override
    public void setCoercableParameterValue(double value) {
        twindowSize = value;
    }

    /**
     * called after every invocation of this operator to see whether
     * a parameter can be optimised for better acceptance hence faster
     * mixing
     *
     * @param logAlpha difference in posterior between previous state & proposed state + hasting ratio
     */
    @Override
    public void optimize(double logAlpha) {
        // must be overridden by operator implementation to have an effect
        double delta = calcDelta(logAlpha);

        delta += Math.log(twindowSize);
        twindowSize = Math.exp(delta);
    }

    @Override
    public final String getPerformanceSuggestion() {
        double prob = m_nNrAccepted / (m_nNrAccepted + m_nNrRejected + 0.0);
        double targetProb = getTargetAcceptanceProbability();

        double ratio = prob / targetProb;
        if (ratio > 2.0) ratio = 2.0;
        if (ratio < 0.5) ratio = 0.5;

        // new scale factor
        double newWindowSize = twindowSize * ratio;

        DecimalFormat formatter = new DecimalFormat("#.###");
        if (prob < 0.10) {
            return "Try setting window size to about " + formatter.format(newWindowSize);
        } else if (prob > 0.40) {
            return "Try setting window size to about " + formatter.format(newWindowSize);
        } else return "";
    }
}

